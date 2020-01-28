package io.bazel.rules_scala.worker;

import java.lang.SecurityManager;
import java.security.Permission;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.testing.junit.runner.util.GoogleTestSecurityManager;

@RunWith(JUnit4.class)
public final class WorkerTest {

    final class CheckArgsWorker implements Worker.Interface {
	private final String[] expected;

	public CheckArgsWorker(String[] expected) {
	    this.expected = expected;
	}

	public void work(String[] args) {
	    assert(args == this.expected);
	}
    }

    final class SystemExitWorker implements Worker.Interface {
	private final int code;

	public SystemExitWorker(int code) {
	    this.code = code;
	}

	public void work(String[] args) {
	    System.exit(code);
	}
    }

    final class ThrowExceptionWorker implements Worker.Interface {
	private final String message;

	public ThrowExceptionWorker(String message) {
	    this.message = message;
	}

	public void work(String[] args) {
	    throw new RuntimeException(this.message);
	}
    }

    @Test
    public void testStandaloneBasicInvocations() {
	String[] args = new String[]{"foo", "bar"};
	Worker.workerMain(args, new CheckArgsWorker(args));

	int code = 123;
	ExitTrapped exitTrapped = assertThrows(
            ExitTrapped.class, () -> Worker.workerMain(args, new SystemExitWorker(code)));
	assert(exitTrapped.code == code);

	String message = "oh no";
	RuntimeException exception =
	    assertThrows(RuntimeException.class, () ->
			 Worker.workerMain(args, new ThrowExceptionWorker(message)));
	assert(exception.getMessage() == message);
    }

    @Test
    public void testStandaloneBadArgsFileInvocation() {
	String[] args = new String[]{"foo", "bar"};
	ExitTrapped exitTrapped = assertThrows(ExitTrapped.class,
		     () -> Worker.workerMain(new String[]{"@lol"}, new CheckArgsWorker(args)));
	assert(exitTrapped.code == 1);
    }

    @Before
    public void init() {
	GoogleTestSecurityManager.uninstallIfInstalled();
	System.setSecurityManager(new SecurityManager() {
		@Override
		public void checkPermission(Permission permission) {
		    Matcher matcher = exitPattern.matcher(permission.getName());
		    if (matcher.find())
			throw new ExitTrapped(Integer.parseInt(matcher.group(1)));
		}
	    });
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

    public static <T extends Throwable> T assertThrows(
						       Class<T> expectedThrowable, ThrowingRunnable runnable) {
    return assertThrows("", expectedThrowable, runnable);
  }

  public static <T extends Throwable> T assertThrows(
      String message, Class<T> expectedThrowable, ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable actualThrown) {
      if (expectedThrowable.isInstance(actualThrown)) {
        @SuppressWarnings("unchecked")
        T retVal = (T) actualThrown;
        return retVal;
      } else {
        throw new AssertionError(
            String.format(
			  "expected %s to be thrown, but %s was thrown",
			  expectedThrowable.getSimpleName(), actualThrown.getClass().getSimpleName()),
            actualThrown);
      }
    }
    String mismatchMessage =
        String.format(
                "expected %s to be thrown, but nothing was thrown",
                expectedThrowable.getSimpleName());
    throw new AssertionError(mismatchMessage);
  }

   public interface ThrowingRunnable {
       void run() throws Throwable;
   }
}
