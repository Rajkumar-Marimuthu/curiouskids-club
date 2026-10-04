package com.curiouskids.club.notification.internal.email;

/** A ready-to-send email with an HTML body and a plain-text alternative (FR-NOT-01). */
public record RenderedEmail(String to, String subject, String html, String text) {}
