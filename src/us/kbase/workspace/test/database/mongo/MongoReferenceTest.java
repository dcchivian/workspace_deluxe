package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Test;

import us.kbase.workspace.database.mongo.MongoReference;

public class MongoReferenceTest {

	@Test
	public void mongoRef() throws Exception {
		Constructor<MongoReference> refConst =
				MongoReference.class.getDeclaredConstructor(String.class);
		refConst.setAccessible(true);
		MongoReference r = refConst.newInstance("1/2/3");
		assertThat("ws id incorrect", r.getWorkspaceID(), is(1L));
		assertThat("obj id incorrect", r.getObjectID(), is(2L));
		assertThat("ver incorrect", r.getVersion(), is(3));
		assertThat("str id incorrect", r.getId(), is("1/2/3"));
		assertThat("to str incorrect", r.toString(), is("1/2/3"));
		
		refConst = MongoReference.class.getDeclaredConstructor(
				long.class, long.class, int.class);
		refConst.setAccessible(true);
		r = refConst.newInstance(4L, 5L, 6);
		assertThat("ws id incorrect", r.getWorkspaceID(), is(4L));
		assertThat("obj id incorrect", r.getObjectID(), is(5L));
		assertThat("ver incorrect", r.getVersion(), is(6));
		assertThat("str id incorrect", r.getId(), is("4/5/6"));
		assertThat("to str incorrect", r.toString(), is("4/5/6"));
	}
	
	@Test
	public void failRefStringConst() throws Exception {
		failMakeRef(null, "reference cannot be null or the empty string");
		failMakeRef("", "reference cannot be null or the empty string");
		failMakeRef("1", "Illegal number of separators / in object reference 1");
		failMakeRef("1/2", "ref 1/2 is not an absolute reference");
		failMakeRef("1/foo", "ref 1/foo is not an absolute reference");
		failMakeRef("foo/1/2", "ref foo/1/2 is not an absolute reference");
		failMakeRef("1/foo/2", "ref 1/foo/2 is not an absolute reference");
		failMakeRef("1/1/foo", "Unable to parse version portion of object reference 1/1/foo to an integer");
		failMakeRef("1/2/3/4", "Illegal number of separators / in object reference 1/2/3/4");
	}
	
	@Test
	public void failRefNumberConst() throws Exception {
		failMakeRef(-1L, 2L, 3, "All arguments must be > 0");
		failMakeRef(1L, -2L, 3, "All arguments must be > 0");
		failMakeRef(1L, 2L, -3, "All arguments must be > 0");
	}

	private void failMakeRef(String ref, String exp) throws Exception {
		Constructor<MongoReference> refConst =
				MongoReference.class.getDeclaredConstructor(String.class);
		refConst.setAccessible(true);
		try {
			refConst.newInstance(ref);
		} catch (InvocationTargetException ite) {
			IllegalArgumentException iae =
					(IllegalArgumentException) ite.getCause();
			assertThat("incorrect message, trace:\n" +
					ExceptionUtils.getMessage(iae),
					iae.getLocalizedMessage(), is(exp));
		}
	}
	
	private void failMakeRef(Long wsid, Long objid, Integer ver, String exp)
			throws Exception {
		Constructor<MongoReference> refConst =
				MongoReference.class.getDeclaredConstructor(
						long.class, long.class, int.class);
		refConst.setAccessible(true);
		try {
			refConst.newInstance(wsid, objid, ver);
		} catch (InvocationTargetException ite) {
			IllegalArgumentException iae =
					(IllegalArgumentException) ite.getCause();
			assertThat("incorrect message, trace:\n" +
					ExceptionUtils.getMessage(iae),
					iae.getLocalizedMessage(), is(exp));
		}
	}
	
}
