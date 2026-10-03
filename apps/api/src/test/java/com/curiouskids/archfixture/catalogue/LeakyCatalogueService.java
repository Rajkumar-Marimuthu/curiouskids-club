package com.curiouskids.archfixture.catalogue;

import com.curiouskids.archfixture.circulation.internal.LoanRecord;

/** Fixture for ArchitectureTest: catalogue reaching into circulation's internals. */
public class LeakyCatalogueService {

  public LoanRecord peek() {
    return new LoanRecord();
  }
}
