/*
 * Copyright (C) 2018 Robocells 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class runPython {

  @WorkerThread
  public static CommandResult runMobpython() {
    int exitCode;

    String pyScript = "print(\"hello world\")";

    List<String> stdout = Collections.synchronizedList(new ArrayList<String>());
    List<String> stderr = Collections.synchronizedList(new ArrayList<String>());

    // Set MobPython binary location
    String shell = "/data/data/com.mobpython.basic/files/eabi/bin/python3.5m -c '";
    String [] mobPythonEnv = {
            "LD_LIBRARY_PATH=.:/data/data/com.mobpython.basic/files/eabi/lib",
    };
    String[] cmd = { "sh", "-c", shell + pyScript + "'" };

    try {
      // setup the process, retrieve stdin stream, and stdout/stderr gobblers
      //Process process = runWithEnv(shell, mobPythonEnv);
      Process process = Runtime.getRuntime().exec(cmd, mobPythonEnv);

      DataOutputStream stdin = new DataOutputStream(process.getOutputStream());

      StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream(), stderr);
      StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream(), stdout);

      // start gobbling and write our commands to the shell
      stderrGobbler.start();
      stdoutGobbler.start();

      try {
        stdin.write("exit\n".getBytes("UTF-8"));
        stdin.flush();
      } catch (IOException e) {
        if (e.getMessage().contains("EPIPE") || e.getMessage().contains("Stream closed")) {
        } else {
          throw e;
        }
      }

      exitCode = process.waitFor();

      try {
        stdin.close();
      } catch (IOException e) {
      }
      stdoutGobbler.join();
      stderrGobbler.join();

      process.destroy();

    } catch (InterruptedException e) {
      exitCode = ShellExitCode.WATCHDOG_EXIT;
    } catch (IOException e) {
      exitCode = ShellExitCode.SHELL_WRONG_UID;
    }
    // Now stdout, stderr, exitCode have the python script execution result
    return new CommandResult(stdout, stderr, exitCode);
  }
}
