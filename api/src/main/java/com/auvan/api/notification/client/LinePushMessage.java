package com.auvan.api.notification.client;

/**
 * One push to one student.
 *
 * @param to       the recipient's LINE user id, which is the {@code sub} this
 *                 system stores as {@code AppUser.lineSubject}
 * @param text     what the student reads
 * @param retryKey the outbox row's id, unchanged across every retry of that
 *                 row. The transport is at-least-once and cannot be made
 *                 otherwise; this key is what makes the <em>user-visible</em>
 *                 effect once-only, because LINE deduplicates on it. Drop it
 *                 and a retried send becomes a second message.
 */
public record LinePushMessage(String to, String text, String retryKey) { }
