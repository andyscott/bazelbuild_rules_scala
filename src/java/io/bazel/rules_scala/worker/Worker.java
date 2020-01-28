package io.bazel.rules_scala.worker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.SecurityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.devtools.build.lib.worker.WorkerProtocol;

public final class Worker {

    /**
     * Scala and Java workers need to provide a main method that calls
     * `Worker.workerMain` with a valid `Worker.Interface`.
     */
    public static interface Interface {
	public void work(String args[]);
    }

    public static final void workerMain(String args[], Interface workerInterface) {
	if (args.length > 0 && args[0].equals("--persistent_worker")) {

	    System.setSecurityManager(new SecurityManager() {
		    @Override
		    public void checkPermission(Permission permission) {
			Matcher matcher = exitPattern.matcher(permission.getName());
			if (matcher.find())
			    throw new ExitTrapped(Integer.parseInt(matcher.group(1)));
		    }
		}
	    );

	    InputStream stdin = System.in;
	    PrintStream stdout = System.out;
	    PrintStream stderr = System.err;
	    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
	    PrintStream out = new PrintStream(outStream);

	    System.setIn(new ByteArrayInputStream(new byte[0]));
	    System.setOut(out);
	    System.setErr(out);

	    try {
		while (true) {
		    WorkerProtocol.WorkRequest request =
			WorkerProtocol.WorkRequest.parseDelimitedFrom(stdin);

		    int code = 0;

		    try {
			workerInterface.work(toArray(request.getArgumentsList()));
		    } catch (ExitTrapped e) {
			code = e.code;
		    } catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			code = 1;
		    }

		    WorkerProtocol.WorkResponse.newBuilder()
			.setOutput(outStream.toString())
			.setExitCode(code)
			.build()
			.writeDelimitedTo(stdout);

		    out.flush();
		    outStream.reset();
		}
	    } catch (IOException e) {
	    } finally {
		System.setIn(stdin);
		System.setOut(stdout);
		System.setErr(stderr);
	    }
	} else {
	    if (args.length == 1 && args[0].startsWith("@")) {
		try {
		    args = toArray(Files.readAllLines(
		        Paths.get(args[0].substring(1)), StandardCharsets.UTF_8));
		} catch (IOException e) {
		    System.err.println("Error reading arguments file " + args[0] +
				       "before delegating to worker implementation");
		    System.exit(1);
		}
	    }
	    workerInterface.work(args);
	}
    }

    private static final class ExitTrapped extends RuntimeException {
	final int code;
	ExitTrapped(int code) {
	    super();
	    this.code = code;
	}
    }

    private static final Pattern exitPattern =
	Pattern.compile("exitVM\\.(-?\\d+)");

    private static String[] toArray(List<String> list) {
	int n = list.size();
	String[] res = new String[n];
	for (int i = 0; i < n; i++) {
	    res[i] = list.get(i);
	}
	return res;
    }
}
