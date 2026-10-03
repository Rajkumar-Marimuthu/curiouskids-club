package com.curiouskids.club;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {

  private static final String ROOT = "com.curiouskids.club";

  private static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  @Test
  @DisplayName("NFR-11: no module uses another module's internal package")
  void noAccessToOtherModulesInternals() {
    noOtherModulesInternals(ROOT).check(MAIN);
  }

  @Test
  @DisplayName("NFR-11: the internal-package rule catches a module importing another's internals")
  void internalRuleCatchesViolation() {
    String fixtureRoot = "com.curiouskids.archfixture";
    JavaClasses fixture = new ClassFileImporter().importPackages(fixtureRoot);

    assertThat(noOtherModulesInternals(fixtureRoot).evaluate(fixture).hasViolation()).isTrue();
  }

  @Test
  @DisplayName("NFR-11: controllers live only in a module's internal.web package")
  void controllersOnlyInInternalWeb() {
    classes()
        .that()
        .areAnnotatedWith(RestController.class)
        .or()
        .areAnnotatedWith(Controller.class)
        .should()
        .resideInAPackage(ROOT + ".*.internal.web..")
        .check(MAIN);
  }

  @Test
  @DisplayName("NFR-11: controllers never take or return JPA entities")
  void noEntitiesInControllerSignatures() {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(RestController.class)
        .should(notExposeEntities())
        .check(MAIN);
  }

  @Test
  @DisplayName("NFR-11: business code reads time only from the injected Clock")
  void timeComesFromInjectedClock() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(ROOT + ".shared.internal.ClockConfig")
        .should()
        .callMethod(Instant.class, "now")
        .orShould()
        .callMethod(LocalDate.class, "now")
        .orShould()
        .callMethod(LocalDate.class, "now", ZoneId.class)
        .orShould()
        .callMethod(LocalDateTime.class, "now")
        .orShould()
        .callMethod(LocalDateTime.class, "now", ZoneId.class)
        .orShould()
        .callMethod(LocalTime.class, "now")
        .orShould()
        .callMethod(LocalTime.class, "now", ZoneId.class)
        .orShould()
        .callMethod(ZonedDateTime.class, "now")
        .orShould()
        .callMethod(ZonedDateTime.class, "now", ZoneId.class)
        .orShould()
        .callMethod(OffsetDateTime.class, "now")
        .orShould()
        .callMethod(OffsetDateTime.class, "now", ZoneId.class)
        .orShould()
        .callMethod(Clock.class, "systemUTC")
        .orShould()
        .callMethod(Clock.class, "systemDefaultZone")
        .orShould()
        .callMethod(System.class, "currentTimeMillis")
        .check(MAIN);
  }

  /** Classes under {@code root.<module>} must not depend on {@code root.<other>.internal}. */
  static ArchRule noOtherModulesInternals(String root) {
    return classes()
        .that()
        .resideInAPackage(root + "..")
        .should(
            new ArchCondition<JavaClass>("not depend on another module's internal package") {
              @Override
              public void check(JavaClass javaClass, ConditionEvents events) {
                String own = moduleOf(root, javaClass.getPackageName());
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                  String targetPackage = dependency.getTargetClass().getPackageName();
                  String target = moduleOf(root, targetPackage);
                  if (target != null
                      && !target.equals(own)
                      && (targetPackage + ".").startsWith(root + "." + target + ".internal.")) {
                    events.add(
                        SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                  }
                }
              }
            });
  }

  private static String moduleOf(String root, String packageName) {
    if (!packageName.startsWith(root + ".")) {
      return null;
    }
    String rest = packageName.substring(root.length() + 1);
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  private static ArchCondition<JavaMethod> notExposeEntities() {
    return new ArchCondition<>("not take or return a JPA entity") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Stream.concat(Stream.of(method.getReturnType()), method.getParameterTypes().stream())
            .flatMap(type -> type.getAllInvolvedRawTypes().stream())
            .filter(type -> type.isAnnotatedWith(Entity.class))
            .forEach(
                entity ->
                    events.add(
                        SimpleConditionEvent.violated(
                            method, method.getFullName() + " exposes entity " + entity.getName())));
      }
    };
  }
}
