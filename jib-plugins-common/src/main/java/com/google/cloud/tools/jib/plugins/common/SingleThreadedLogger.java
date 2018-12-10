/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.plugins.common;

import com.google.common.util.concurrent.Futures;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Keeps all log messages in a sequential, deterministic order along with an additional footer that
 * always appears below log messages.
 */
class SingleThreadedLogger {

  /** ANSI escape sequence for moving the cursor up one line. */
  private static final String CURSOR_UP_SEQUENCE = "\033[1A";

  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  private final Consumer<String> plainLogger;

  private String footer = "";
  private int footerLineCount = 0;

  SingleThreadedLogger(Consumer<String> plainLogger) {
    this.plainLogger = plainLogger;
  }

  /**
   * Runs {@code messageLogger} asynchronously.
   *
   * @param messageLogger the {@link Runnable} intended to synchronously log a message to the
   *     console
   * @return a {@link Future} to track completion
   */
  public Future<Void> log(Runnable messageLogger) {
    return log(messageLogger, footer, footerLineCount);
  }

  /**
   * Sets the footer asynchronously. This will replace the previously-printed footer with the new {@code footer}.
   *
   * @param footer the footer
   * @param lineCount the number of lines in the footer
   * @return a {@link Future} to track completion
   */
  public Future<Void> setFooter(String footer, int lineCount) {
    if (footer.equals(this.footer)) {
      return Futures.immediateFuture(null);
    }

    Future<Void> future = log(() -> {}, this.footer, this.footerLineCount);

    this.footer = footer;
    this.footerLineCount = lineCount;

    return future;
  }

  private Future<Void> log(Runnable messageLogger, String previousFooter, int previousLineCount) {
    return executorService.submit(
        () -> {
          StringBuilder plainLogBuilder = new StringBuilder();

          // Moves the cursor up to the start of the footer.
          // TODO: Optimize to single init.
          for (int i = 0; i < previousLineCount; i++) {
            // Moves cursor up.
            plainLogBuilder.append(CURSOR_UP_SEQUENCE);
          }

          // Overwrites the footer with whitespace.
          // TODO: Optimize to single init.
          for (int i = 0; i < previousFooter.length(); i++) {
            plainLogBuilder.append(previousFooter.charAt(i) == '\n' ? '\n' : ' ');
          }

          // Moves the cursor up again.
          // TODO: Optimize to single init.
          for (int i = 0; i < previousLineCount; i++) {
            // Moves cursor up.
            plainLogBuilder.append(CURSOR_UP_SEQUENCE);
          }

          // Writes out logMessage and footer.
          plainLogger.accept(plainLogBuilder.toString());
          messageLogger.run();
          plainLogger.accept(footer);

          return null;
        });
  }
}
