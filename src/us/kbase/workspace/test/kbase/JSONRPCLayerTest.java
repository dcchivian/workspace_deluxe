package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;

import us.kbase.auth.AuthUser;
import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.workspace.AlterWorkspaceMetadataParams;
import us.kbase.workspace.CloneWorkspaceParams;
import us.kbase.workspace.CopyObjectParams;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetModuleInfoParams;
import us.kbase.workspace.GetObjectInfoNewParams;
import us.kbase.workspace.ListAllTypesParams;
import us.kbase.workspace.ListModuleVersionsParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ModuleVersions;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.RegisterTypespecCopyParams;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RenameObjectParams;
import us.kbase.workspace.RenameWorkspaceParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.SetWorkspaceDescriptionParams;
import us.kbase.workspace.SubObjectIdentity;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * These tests are specifically for testing the JSON-RPC communications between
 * the client, up to the invocation of the {@link us.kbase.workspace.workspaces.Workspaces}
 * methods. As such they do not test the full functionality of the Workspaces methods;
 * {@link us.kbase.workspace.test.workspaces.TestWorkspaces} handles that. This means
 * that only one backend (the simplest gridFS backend) is tested here, while TestWorkspaces
 * tests all backends and {@link us.kbase.workspace.database.WorkspaceDatabase} implementations.
 */
public class JSONRPCLayerTest extends JSONRPCLayerTester {
	
	@Test
	public void ver() throws Exception {
		assertThat("got correct version", CLIENT_NO_AUTH.ver(), is("0.2.0"));
	}
	
	@Test
	public void createWSandCheck() throws Exception {
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("fry", "laurie");
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("foo")
					.withGlobalread("r")
					.withDescription("boogabooga")
					.withMeta(meta));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> infoget =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity()
						.withWorkspace("foo"));
		checkWS(info, info.getE1(), info.getE4(), "foo", USER1, 0, "a", "r", "unlocked", "boogabooga", meta);
		checkWS(infoget, info.getE1(), info.getE4(), "foo", USER1, 0, "a", "r", "unlocked", "boogabooga", meta);
	}
		
	@Test
	public void setWorkspaceDescription() throws Exception {
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> meta =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("wsdesc"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("wsdesc");
		CLIENT1.setWorkspaceDescription(new SetWorkspaceDescriptionParams()
				.withDescription("foobar").withWorkspace("wsdesc"));
		assertThat("correct ws desc", CLIENT1.getWorkspaceDescription(wsi),
				is("foobar"));
		SetWorkspaceDescriptionParams swdp = new SetWorkspaceDescriptionParams()
				.withDescription("foo").withId(meta.getE1());
		swdp.setAdditionalProperties("baz", "foo");
		failSetWSDesc(swdp, "Unexpected arguments in SetWorkspaceDescriptionParams: baz");
		failSetWSDesc(new SetWorkspaceDescriptionParams(),
				"Must provide one and only one of workspace name (was: null) or id (was: null)");
		failSetWSDesc(new SetWorkspaceDescriptionParams().withWorkspace("foo").withId(1L),
				"Must provide one and only one of workspace name (was: foo) or id (was: 1)");
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("wsdesc").withNewPermission("n"));
	}
	
	@Test
	public void createWSBadGlobal() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("gl1")); //should work fine w/o globalread
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
		.withWorkspace("gl2").withGlobalread("n")); //should work fine w/o globalread
		assertThat("globalread correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("gl1")).getE7(), is("n"));
		assertThat("globalread correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("gl2")).getE7(), is("n"));
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("w"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("a"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("b"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("gl1").withNewPermission("n"));
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("gl2").withNewPermission("n"));
	}
	
	@Test
	public void createWSNoAuth() throws Exception {
		try {
			CLIENT_NO_AUTH.createWorkspace(new CreateWorkspaceParams().withWorkspace("noauth"));
			fail("created workspace without auth");
		} catch (UnauthorizedException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("RPC method requires authentication but neither user nor token was set"));
		}
	}

	@Test
	public void setBadPermissions() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("badperms"));
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withUsers(Arrays.asList(USER2)));
			fail("able to set null permission");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("No such permission: null"));
		
		}
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("f").withUsers(Arrays.asList(USER2)));
			fail("able to set illegal permission");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("No such permission: f"));
		
		}
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("r").withUsers(new ArrayList<String>()));
			fail("able to set permission with no users");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide at least one user"));
		}
		
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("r").withUsers(Arrays.asList(USER2,
					"thisisnotarealuserihopeotherwisethistestwillfailandthatdbeabadthing")));
			fail("able to set  permission with bad user");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("User thisisnotarealuserihopeotherwisethistestwillfailandthatdbeabadthing is not a valid user"));
		}
		Map<String, String> expected = new HashMap<String, String>();
		expected.put(USER1, "a");
		Map<String, String> perms = CLIENT1.getPermissions(new WorkspaceIdentity().withWorkspace("badperms"));
		assertThat("Bad permissions were added to a workspace", perms, is(expected));
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
		.withWorkspace("badperms").withNewPermission("n"));
	}
	
	@Test
	public void permissions() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("permspriv")
				.withDescription("foo"));
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("permsglob")
				.withGlobalread("r").withDescription("bar"));
		//should work, global read
		CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permsglob"));
		CLIENT_NO_AUTH.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permsglob"));
		CLIENT_NO_AUTH.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("permsglob"));
		
		try {
			CLIENT_NO_AUTH.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("able to read workspace desc with no auth");
		} catch (ServerException e) {
			assertThat("exception message corrent", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace permspriv"));
		}
		
		try {
			CLIENT_NO_AUTH.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("able to read workspace desc with no auth");
		} catch (ServerException e) {
			assertThat("exception message corrent", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace permspriv"));
		}
		
		try {
			CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("Able to get ws desc without read perms");
		} catch (ServerException e) {
			assertThat("Correct excp message", e.getLocalizedMessage(),
					is("User "+USER2+" may not read workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("r").withUsers(Arrays.asList(USER2)));
		CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv")); //should work, now readable
		
		Map<String, String> data = new HashMap<String, String>();
		data.put("foo", "bar");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE));
		try {
			CLIENT2.saveObjects(new SaveObjectsParams()
				.withWorkspace("permspriv").withObjects(objects));
		} catch (ServerException e) {
			assertThat("correcet exception", e.getLocalizedMessage(),
					is("User "+USER2+" may not write to workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.saveObjects(new SaveObjectsParams()
			.withWorkspace("permspriv").withObjects(objects)); //should work
		Map<String, String> expected = new HashMap<String, String>();
		expected.put(USER1, "a");
		expected.put(USER2, "w");
		Map<String, String> perms = CLIENT1.getPermissions(new WorkspaceIdentity()
			.withWorkspace("permspriv"));
		assertThat("Permissions set correctly", perms, is(expected));
		
		try {
			CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
					.withNewPermission("a").withUsers(Arrays.asList(USER1)));
		} catch (ServerException e) {
			assertThat("Correct excp message", e.getLocalizedMessage(),
					is("User "+USER2+" may not alter other user's permissions on workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("a").withUsers(Arrays.asList(USER2)));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("w").withUsers(Arrays.asList(USER3))); //should work
		expected.put(USER1, "a");
		expected.put(USER2, "a");
		expected.put(USER3, "w");
		perms = CLIENT2.getPermissions(new WorkspaceIdentity()
			.withWorkspace("permspriv"));
		assertThat("Permissions set correctly", perms, is(expected));
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("permspriv").withNewPermission("n"));
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("permsglob").withNewPermission("n"));
	}
	
	@Test
	public void badIdent() throws Exception {
		try {
			CLIENT1.getPermissions(new WorkspaceIdentity());
			fail("got non-existant workspace");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide one and only one of workspace name (was: null) or id (was: null)"));
		}
	}
	
	@Test
	public void workspaceIDprocessing() throws Exception {
		String ws = "idproc";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws)
				.withDescription("foo"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> meta =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace(ws));
		//these should work
		CLIENT1.setPermissions(new SetPermissionsParams().withId(meta.getE1())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace(meta.getE2())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		
		try {
			CLIENT1.setPermissions(new SetPermissionsParams()
					.withUsers(Arrays.asList(USER2)).withNewPermission("w"));
			fail("able set perms without providing ws id or name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide one and only one of workspace name (was: null) or id (was: null)"));
		}
		
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withId(meta.getE1())
					.withNewPermission("w").withUsers(Arrays.asList(USER2))
					.withWorkspace(meta.getE2()));
			fail("able to specify workspace by id and name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is(String.format(
					"Must provide one and only one of workspace name (was: idproc) or id (was: %s)",
					meta.getE1())));
		}
		
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withId(-1L)
					.withNewPermission("w").withUsers(Arrays.asList(USER2)));
			fail("able to specify workspace by id and name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Workspace id must be > 0"));
		}
		
		//should work 
		CLIENT1.setPermissions(new SetPermissionsParams()
				.withWorkspace("kb|ws." + meta.getE1())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("kb|ws." + meta.getE1()));
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Illegal character in workspace name kb|ws." 
					+ meta.getE1() + ": |"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace(TEXT256));
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Workspace name exceeds the maximum length of 255"));
		}
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace(ws).withNewPermission("n"));
	}
	
	@Test
	public void saveBadPackages() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("savebadpkg")
				.withDescription("foo"));
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		objects.add(new ObjectSaveData().withData(new UObject("some crap"))
				.withType("SomeRandom.Type"));
		SaveObjectsParams sop = new SaveObjectsParams()
			.withWorkspace("permspriv").withObjects(objects);
		sop.setAdditionalProperties("foo", "bar");
		sop.setAdditionalProperties("baz", "faz");
		try {
			CLIENT1.saveObjects(sop);
			fail("allowed unexpected args");
		} catch (ServerException e) {
			String[] exp = e.getLocalizedMessage().split(":");
			String[] args = exp[1].trim().split("\\s");
			HashSet<String> argset = new HashSet<String>(Arrays.asList(args));
			assertThat("correct exception message", exp[0],
					is("Unexpected arguments in SaveObjectsParams"));
			assertThat("correct args list", argset,
					is(new HashSet<String>(Arrays.asList("foo", "baz"))));
		}
		
		objects.get(0).setAdditionalProperties("wugga", "boo");
		saveBadObject(objects, "Unexpected arguments in ObjectSaveData: wugga");
		
		objects.set(0, new ObjectSaveData().withName("myname").withObjid(1L));
		saveBadObject(objects, "Must provide one and only one of object name (was: myname) or id (was: 1)");
		
		objects.set(0, new ObjectSaveData().withName("myname+"));
		saveBadObject(objects, "Illegal character in object name myname+: +");
		
		objects.set(0, new ObjectSaveData().withName(TEXT256));
		saveBadObject(objects, "Object name exceeds the maximum length of 255");
		
		objects.set(0, new ObjectSaveData().withObjid(0L));
		saveBadObject(objects, "Object id must be > 0");
		
		objects.set(0, new ObjectSaveData());
		saveBadObject(objects, "Object 1 has no data");
		
		objects.add(0, new ObjectSaveData().withData(new UObject("foo")).withType("Foo.Bar"));
		saveBadObject(objects, "Object 2 has no data");
		
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject("foo")));
		saveBadObject(objects, "Object 1 type error: Typestring cannot be null or the empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType(null));
		saveBadObject(objects, "Object 1 type error: Typestring cannot be null or the empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType(""));
		saveBadObject(objects, "Object 1 type error: Typestring cannot be null or the empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("foo"));
		saveBadObject(objects, "Object 1 type error: Type foo could not be split into a module and name");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("foo.bar-1.2.3"));
		saveBadObject(objects, "Object 1 type error: Type version string 1.2.3 could not be parsed to a version");
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("savebadpkg").withNewPermission("n"));
	}
	
	@Test
	public void saveProvenance() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("provenance"));
		long wsid = CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("provenance")).getE1();
		UObject data = new UObject(new HashMap<String, Object>());
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(data)
						.withType(SAFE_TYPE))));
		
		SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(objects);
		List<ProvenanceAction> prov = Arrays.asList(
				new ProvenanceAction()
					.withDescription("desc")
					.withInputWsObjects(Arrays.asList("provenance/auto1/1"))
					.withIntermediateIncoming(Arrays.asList("a", "b", "c"))
					.withIntermediateOutgoing(Arrays.asList("d", "e", "f"))
					.withMethod("meth")
					.withMethodParams(Arrays.asList(new UObject("foo"),
							new UObject(new HashMap<String, String>()),
							new UObject(Arrays.asList("foo", "bar"))))
					.withResolvedWsObjects(Arrays.asList("will be ignored"))
					.withScript("script")
					.withScriptCommandLine("cmd line")
					.withScriptVer("1")
					.withService("serv")
					.withServiceVer("2")
					.withTime("2013-04-26T12:52:06-0800"),
				new ProvenanceAction());
		objects.add(new ObjectSaveData().withData(data).withType(SAFE_TYPE)
				.withProvenance(prov));
		CLIENT1.saveObjects(sop);
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("provenance/auto1/1", wsid + "/1/1");
		Map<String, String> timemap = new HashMap<String, String>();
		timemap.put("2013-04-26T12:52:06-0800", "2013-04-26T20:52:06+0000");
		ObjectIdentity id = new ObjectIdentity().withWorkspace("provenance").withObjid(2L);
		checkProvenance(USER1, id, prov, refmap, timemap);
		
		ProvenanceAction pa = new ProvenanceAction();
		pa.setAdditionalProperties("foo", "bar");
		objects.set(0, new ObjectSaveData().withData(data).withType(SAFE_TYPE)
				.withProvenance(Arrays.asList(pa)));
		try {
			CLIENT1.saveObjects(sop);
			fail("save w/ prov w/ extra fields");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Unexpected arguments in ProvenanceAction: foo"));
		}
		
		saveProvWithGoodTime("provenance", "2013-04-26T23:52:06-0800",
				"2013-04-27T07:52:06+0000");
		saveProvWithGoodTime("provenance", "2013-04-26T23:52:06Z",
				"2013-04-26T23:52:06+0000");
		saveProvWithGoodTime("provenance", "2013-04-26T23:52:06.145Z",
				"2013-04-26T23:52:06+0000");
		saveProvWithGoodTime("provenance", "2013-04-26T23:52:06.14Z",
				"2013-04-26T23:52:06+0000");
		saveProvWithGoodTime("provenance", "2013-04-26T23:52:06.1Z",
				"2013-04-26T23:52:06+0000");
		saveProvWithGoodTime("provenance", "2013-04-26T23:52:06.14-0800",
				"2013-04-27T07:52:06+0000");
		
		saveProvWithBadTime("2013-04-26T25:52:06-0800",
				"Unparseable date: Cannot parse \"2013-04-26T25:52:06-0800\": Value 25 for hourOfDay must be in the range [0,23]");
		saveProvWithBadTime("2013-04-26T23:52:06-8000",
				"Unparseable date: Invalid format: \"2013-04-26T23:52:06-8000\" is malformed at \"8000\"");
		saveProvWithBadTime("2013-04-35T23:52:06-0800",
				"Unparseable date: Cannot parse \"2013-04-35T23:52:06-0800\": Value 35 for dayOfMonth must be in the range [1,30]");
		saveProvWithBadTime("2013-13-26T23:52:06-0800",
				"Unparseable date: Cannot parse \"2013-13-26T23:52:06-0800\": Value 13 for monthOfYear must be in the range [1,12]");
		saveProvWithBadTime("2013-13-26T23:52:06.1111-0800",
				"Unparseable date: Invalid format: \"2013-13-26T23:52:06.1111-0800\" is malformed at \"1-0800\"");
		saveProvWithBadTime("2013-13-26T23:52:06.-0800",
				"Unparseable date: Invalid format: \"2013-13-26T23:52:06.-0800\" is malformed at \".-0800\"");
		saveProvWithBadTime("2013-12-26T23:52:06.55",
				"Unparseable date: Invalid format: \"2013-12-26T23:52:06.55\" is too short");
		saveProvWithBadTime("2013-12-26T23:52-0800",
				"Unparseable date: Invalid format: \"2013-12-26T23:52-0800\" is malformed at \"-0800\"");
		
		CLIENT1.setPermissions(new SetPermissionsParams().withId(wsid)
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(data)
						.withType(SAFE_TYPE).withName("whoops"))));
		checkProvenance(USER2, new ObjectIdentity().withName("whoops")
				.withWorkspace("provenance"), new ArrayList<ProvenanceAction>(),
				new HashMap<String, String>(), new HashMap<String, String>());
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("provenance").withNewPermission("n"));
	}

	private void saveProvWithGoodTime(String workspace, String inputTime,
			String expectedTime) throws Exception {
		UObject data = new UObject(new HashMap<String, Object>());
		
		List<ProvenanceAction> prov = Arrays.asList(
				new ProvenanceAction().withTime(inputTime));
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		objects.add(new ObjectSaveData().withData(data).withType(SAFE_TYPE)
				.withProvenance(prov));
		SaveObjectsParams sop = new SaveObjectsParams().withWorkspace(workspace)
				.withObjects(objects);
		Long objid = CLIENT1.saveObjects(sop).get(0).getE1();
		Map<String, String> refmap = new HashMap<String, String>();
		Map<String, String> timemap = new HashMap<String, String>();
		timemap.put(inputTime, expectedTime);
		ObjectIdentity id = new ObjectIdentity().withWorkspace(workspace).withObjid(objid);
		checkProvenance(USER1, id, prov, refmap, timemap);
		
	}

	@Test
	public void saveObjectsWithLargeString() throws Exception {
		String wsName = "largestring";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
		String largeString = generateLargeString(1234567);  // longer than 1 megabyte
		Map<String, Object> data = new LinkedHashMap<String, Object>();
		data.put("z", "1");
		data.put("thing", largeString);  // data is not sorted
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(wsName)
				.withObjects(Arrays.asList(
						new ObjectSaveData().withName("obj1").withType(SAFE_TYPE).withData(new UObject(data)),
						new ObjectSaveData().withName("obj2").withType(SAFE_TYPE).withData(new UObject(data)))));
		List<ObjectData> ret = CLIENT1.getObjects(Arrays.asList(
				new ObjectIdentity().withRef(wsName + "/obj2"),
				new ObjectIdentity().withRef(wsName + "/obj1")));
		for (ObjectData obj : ret) {
			String largeString2 = (String)obj.getData().asClassInstance(Map.class).get("thing");
			if (!largeString2.equals(largeString))
				Assert.fail("Observed large string is: " + largeString2);
		}
	}
	
	private static String generateLargeString(int length) {
		char[] chars = new char[length];
		for (int i = 0; i < length; i++)
			chars[i] = (char)(32 + (i % (127 - 32)));
		return new String(chars);
	}
	
	@Test
	public void saveAndGetObjects() throws Exception {
		
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("saveget"));
		long wsid = CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("saveget")).getE1();
		
		//save some objects to get
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> data2 = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		data2.put("fubar2", moredata);
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("saveget")
				.withObjects(objects);
		
		try {
			CLIENT1.saveObjects(soc);
			fail("called save with no data");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("No data provided"));
		}
		
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withMeta(meta).withType(SAFE_TYPE)); // will be "1"
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withMeta(meta).withType(SAFE_TYPE)); // will be "2"
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMeta(meta2).withType(SAFE_TYPE).withName("foo"));
		
		List<Tuple11<Long, String, String, String, Long, String, Long, String,
			String, Long, Map<String, String>>> retmet =
				CLIENT1.saveObjects(soc);

		assertThat("max obj count correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("saveget")).getE5(), is(3L));
		
		assertThat("num metas correct", retmet.size(), is(3));
		checkInfo(retmet.get(0), 1, "auto1", SAFE_TYPE, 1, USER1, wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta);
		checkInfo(retmet.get(1), 2, "auto2", SAFE_TYPE, 1, USER1, wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta);
		checkInfo(retmet.get(2), 3, "foo", SAFE_TYPE, 1, USER1, wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		
		
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMeta(meta2).withType(SAFE_TYPE).withObjid(2L));
		
		retmet = CLIENT1.saveObjects(soc);
		
		assertThat("num metas correct", retmet.size(), is(1));
		checkInfo(retmet.get(0), 2, "auto2", SAFE_TYPE, 2, USER1, wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		
		List<ObjectIdentity> loi = new ArrayList<ObjectIdentity>();
		loi.add(new ObjectIdentity().withRef("saveget/2/1"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2.ver.1"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2/1"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2").withVer(1L));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L).withVer(1L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2").withVer(1L));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L).withVer(1L));
		checkSavedObjects(loi, 2, "auto2", SAFE_TYPE, 1, USER1,
				wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta, data);
		
		loi.clear();
		// w/o versions
		loi.add(new ObjectIdentity().withRef("saveget/2"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2"));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L));
		// w/ versions
		loi.add(new ObjectIdentity().withRef("saveget/2/2"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2.ver.2"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2/2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2").withVer(2L));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L).withVer(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2").withVer(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L).withVer(2L));
		
		checkSavedObjects(loi, 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2, data2);
		
		failGetObjects(new ArrayList<ObjectIdentity>(), "No object identifiers provided");
		
		// try some bad refs and id/name combos
		loi.clear();
		loi.add(new ObjectIdentity().withRef("saveget/2"));
		loi.add(new ObjectIdentity().withRef("kb|wss." + wsid + ".obj.2"));
		failGetObjects(loi, "Error on ObjectIdentity #2: Illegal number of separators / in object reference kb|wss." + wsid + ".obj.2");
		
		loi.set(1, new ObjectIdentity().withRef("saveget/1"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid));
		failGetObjects(loi, "Error on ObjectIdentity #3: Illegal number of separators / in object reference kb|ws." + wsid);
		
		//there are 32 different ways to get this type of error. Just try a few.
		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withName("2"));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Object name: 2");
		
		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withObjid(2L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Object id: 2");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withVer(2L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Version: 2");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWorkspace("saveget"));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Workspace: saveget");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWsid(wsid));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Workspace id: " + wsid);
		
		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWsid(wsid).withWorkspace("saveget").withVer(2L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Workspace: saveget Workspace id: " + wsid + " Version: 2");
		
		ObjectIdentity oi = new ObjectIdentity().withRef("saveget/1");
		oi.setAdditionalProperties("foo", "bar");
		loi.set(2, oi);
		failGetObjects(loi, "Error on ObjectIdentity #3: Unexpected arguments in ObjectIdentity: foo");
		
		loi.set(2, new ObjectIdentity().withWorkspace("kb|wss." + wsid).withObjid(2L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Illegal character in workspace name kb|wss." + wsid + ": |");
		
		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(-1L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object id must be > 0");
		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(1L).withVer(0L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object version must be > 0");
		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(1L).withVer(Integer.MAX_VALUE + 1L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Maximum object version is " + Integer.MAX_VALUE);
		
		loi.set(2, new ObjectIdentity().withWorkspace("ultrafakeworkspace").withObjid(1L).withVer(1L));
		failGetObjects(loi, "Object 1 cannot be accessed: No workspace with name ultrafakeworkspace exists");
		loi.set(2, new ObjectIdentity().withWsid(20000000000000000L).withObjid(1L).withVer(1L));
		failGetObjects(loi, "Object 1 cannot be accessed: No workspace with id 20000000000000000 exists");
		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(300L).withVer(1L));
		failGetObjects(loi, "No object with id 300 exists in workspace " + wsid);
		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withName("ultrafakeobj").withVer(1L));
		failGetObjects(loi, "No object with name ultrafakeobj exists in workspace " + wsid);
		
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("setgetunreadableto1"));
		loi.set(2, new ObjectIdentity().withWorkspace("setgetunreadableto1").withObjid(1L).withVer(1L));
		failGetObjects(loi, "Object 1 cannot be accessed: User " + USER1 + " may not read workspace setgetunreadableto1");
		
		//test get_object_info w/o errors
		GetObjectInfoNewParams p = new GetObjectInfoNewParams().withObjects(Arrays.asList(loi.get(0)));
		p.setAdditionalProperties("wooga", "foo");
		failGetObjectInfoNew(p, "Unexpected arguments in GetObjectInfoNewParams: wooga");
		failGetObjectInfoNew(new GetObjectInfoNewParams().withObjects(null),
				"The object identifier list cannot be null");
		
		List<ObjectIdentity> nullloi = new ArrayList<ObjectIdentity>();
		nullloi.add(new ObjectIdentity().withWorkspace("ultrafakeworkspace").withObjid(1L).withVer(1L));
		nullloi.add(new ObjectIdentity().withWsid(20000000000000000L).withObjid(1L).withVer(1L));
		nullloi.add(new ObjectIdentity().withRef("saveget/2"));
		nullloi.add(new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(300L).withVer(1L));
		nullloi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2"));
		nullloi.add(new ObjectIdentity().withRef(wsid + "/2"));
		nullloi.add(new ObjectIdentity().withWorkspace("kb|ws." + wsid).withName("ultrafakeobj").withVer(1L));
		nullloi.add(new ObjectIdentity().withWorkspace("setgetunreadableto1").withObjid(1L).withVer(1L));
		
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> nullret =
				CLIENT1.getObjectInfoNew(new GetObjectInfoNewParams().withObjects(nullloi)
				.withIgnoreErrors(1L).withIncludeMetadata(1L));
		
		assertNull("Got object info when expected null", nullret.get(0));
		assertNull("Got object info when expected null", nullret.get(1));
		checkInfo(nullret.get(2), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret.get(3));
		checkInfo(nullret.get(4), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		checkInfo(nullret.get(5), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret.get(6));
		assertNull("Got object info when expected null", nullret.get(7));
		
		CLIENT2.setPermissions(new SetPermissionsParams().withNewPermission("r")
				.withUsers(Arrays.asList(USER1)).withWorkspace("setgetunreadableto1"));
		CLIENT2.deleteWorkspace(new WorkspaceIdentity().withWorkspace("setgetunreadableto1"));
		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withRef("saveget/1")));
		
		nullloi.set(2, new ObjectIdentity().withRef("saveget/1"));
		nullloi.set(5, new ObjectIdentity().withWorkspace("setgetunreadableto1")
				.withName("foo"));
		
		nullret = CLIENT1.getObjectInfoNew(new GetObjectInfoNewParams().withObjects(nullloi)
				.withIgnoreErrors(1L).withIncludeMetadata(1L));
		
		assertNull("Got object info when expected null", nullret.get(0));
		assertNull("Got object info when expected null", nullret.get(1));
		assertNull("Got object info when expected null", nullret.get(2));
		assertNull("Got object info when expected null", nullret.get(3));
		checkInfo(nullret.get(4), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret.get(5));
		assertNull("Got object info when expected null", nullret.get(6));
		assertNull("Got object info when expected null", nullret.get(7));
		
		
		//clean up
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("saveget").withNewPermission("n"));
	}
	
	@Test
	public void encodings() throws Exception {
		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("encodings")).getE1();
		
		StringBuffer sb = new StringBuffer();
		sb.appendCodePoint(0x1F082);
		sb.append("a");
		sb.appendCodePoint(0x1F0C6);
		sb.append("b");
		sb.appendCodePoint(0x23824);
		sb.append("c");
		sb.appendCodePoint(0x1685);
		sb.append("d");
		sb.appendCodePoint(0x13B2);
		sb.append("e");
		sb.appendCodePoint(0x06E9);
		
		
		//13 bytes in utf-8, 22 in 16, 40 in 32, byte -> file cutoff is 24
		String smallData = "{\"f\":\"" + sb.toString().substring(0, 3) + "\"}";
		@SuppressWarnings("unchecked")
		Map<String, Object> smallmapdata = MAPPER.readValue(smallData, Map.class);
		
		String s = sb.toString() + sb.toString();
		String data = "{\"" + s + "42\":[\"" + s + "\",\"" + s + "woot\",\"" +
																s + "\"]," +
					   "\"" + s + "6\":\"" + s + "\"," +
					   "\"" + s + "3012\":1}";
		@SuppressWarnings("unchecked")
		Map<String, Object> mapdata = MAPPER.readValue(data, Map.class);
		
		String req = "{\"method\":\"Workspace.save_objects\"," +
					  "\"version\":\"1.1\"," +
					  "\"id\":\"" + ("" + Math.random()).substring(2) + "\"," +
					  "\"params\":[{\"id\":" + wsid + "," +
								   "\"objects\": [{\"data\":%s," +
												  "\"type\":\"" + SAFE_TYPE + "\"" +
												  "}" +
												 "]" +
								   "}" +
								  "]" +
					  "}";
		
		List<Charset> csets = Arrays.asList(Charset.forName("UTF-8"),
				Charset.forName("UTF-16LE"), Charset.forName("UTF-16BE"),
				Charset.forName("UTF-32LE"), Charset.forName("UTF-32BE"));
		
		for (String d: Arrays.asList(data, smallData)) {
			for (Charset cs: csets) {
				byte[] breq = String.format(req, d).getBytes(cs);

				HttpURLConnection conn = (HttpURLConnection) CLIENT1.getURL()
						.openConnection();
				conn.setConnectTimeout(10000);
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Authorization", CLIENT1.getToken().toString());
				conn.getOutputStream().write(breq);
				conn.getResponseCode();
				InputStream is = conn.getInputStream();
				int read = 1;
				while (read > -1) {
					read = is.read(breq);
				}
				is.close();
			}
		}
		for (long i = 1; i < 11; i++) {
			Map<String, Object> exp;
			if (i < 6) {
				exp = mapdata;
			} else {
				exp = smallmapdata;
			}
			Map<String, Object> ret = CLIENT1.getObjects(
					Arrays.asList(new ObjectIdentity().withWsid(wsid)
							.withObjid(i))).get(0).getData().asInstance();
			assertThat("Got correct object back with sending various byte encodings to server",
					ret, is(exp));
		}
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void deprecatedMethods() throws Exception {
		CLIENT1.requestModuleOwnership("DepAnotherModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "DepAnotherModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams().withDryrun(0L)
			.withNewTypes(Arrays.asList("AType"))
			.withSpec(
					"module DepAnotherModule {" +
						"/* @optional thing */" +
						"typedef structure {" +
							"string thing;" +
						"} AType;" +
					"};")
			);
		String anotherType = "DepAnotherModule.AType-0.1";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("depsave"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("depsave2")
				.withGlobalread("r"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo = CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("depsave"));
		long wsid = wsinfo.getE1();
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("depsave")
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		
		checkDepWSMeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withWorkspace("depsave"),
				"depsave", USER1, wsinfo.getE4(), 0, "a", "n", wsid);
		checkDepWSMeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withId(wsid),
				"depsave", USER1, wsinfo.getE4(), 0, "a", "n", wsid);
		checkDepWSMeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withWorkspace("depsave").withAuth(AUTH_USER2.getTokenString()),
				"depsave", USER1, wsinfo.getE4(), 0, "w", "n", wsid);

		Tuple7<String, String, String, Long, String, String, Long> wsmeta =
				CLIENT1.getWorkspacemeta(new us.kbase.workspace.GetWorkspacemetaParams().withWorkspace("depsave"));
		Tuple7<String, String, String, Long, String, String, Long> wsmeta2 =
				CLIENT1.getWorkspacemeta(new us.kbase.workspace.GetWorkspacemetaParams().withWorkspace("depsave2"));
		
		List<Tuple7<String, String, String, Long, String, String, Long>> emptyWS = 
				new ArrayList<Tuple7<String,String,String,Long,String,String,Long>>();
		
		checkWSInfoListDep(CLIENT1.listWorkspaces(new us.kbase.workspace.ListWorkspacesParams()
				.withExcludeGlobal(1L)),
				Arrays.asList(wsmeta), Arrays.asList(wsmeta2));
		checkWSInfoListDep(CLIENT1.listWorkspaces(new us.kbase.workspace.ListWorkspacesParams()),
				Arrays.asList(wsmeta, wsmeta2), emptyWS);
		
		//save some objects to get
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> data2 = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		data2.put("fubar2", moredata);
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		
		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj1 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj1")
				.withMetadata(meta).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)));
		
		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj2 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj2")
				.withMetadata(meta2).withType(anotherType).withWorkspace("depsave")
				.withData(new UObject(data2)));
		
		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj3 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth(AUTH_USER2.getTokenString()));
		
		checkDeprecatedSaveInfo(obj1, 1, "obj1", SAFE_TYPE, 1, USER1, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4", meta);
		checkDeprecatedSaveInfo(obj2, 2, "obj2", anotherType, 1, USER1, wsid, "depsave", "3c59f762140806c36ab48a152f28e840", meta2);
		checkDeprecatedSaveInfo(obj3, 3, "obj3", SAFE_TYPE, 1, USER2, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4", meta2);
		
		checkSavedObjectDep(new ObjectIdentity().withWorkspace("depsave").withName("obj1"),
				new ObjectIdentity().withWsid(wsid).withObjid(1L),
				1, "obj1", SAFE_TYPE, 1, USER1, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4",
				23, meta, data, AUTH_USER2);
		checkSavedObjectDep(new ObjectIdentity().withWorkspace("depsave").withName("obj2"),
				new ObjectIdentity().withWsid(wsid).withObjid(2L),
				2, "obj2", anotherType, 1, USER1, wsid, "depsave", "3c59f762140806c36ab48a152f28e840",
				24, meta2, data2, AUTH_USER2);
		checkSavedObjectDep(new ObjectIdentity().withWorkspace("depsave").withName("obj3"),
				new ObjectIdentity().withWsid(wsid).withObjid(3L),
				3, "obj3", SAFE_TYPE, 1, USER2, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4",
				23, meta2, data, AUTH_USER2);
		
		checkListObjectsDep("depsave", null, null, null, Arrays.asList(obj1, obj2, obj3));
		checkListObjectsDep("depsave", anotherType, null, null, Arrays.asList(obj2));
		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withName("obj2").withWorkspace("depsave")));
		checkListObjectsDep("depsave", null, 0L, null, Arrays.asList(obj1, obj3));
		checkListObjectsDep("depsave", null, 1L, null, Arrays.asList(obj1, obj2, obj3));
		checkListObjectsDep("depsave", null, null, AUTH_USER2.getTokenString(), Arrays.asList(obj1, obj3));
		
		String invalidToken = AUTH_USER2.getTokenString() + "a";
		String invalidTokenExp = "Token is invalid";
		String badFormatToken = "borkborkbork";
		String badFormatTokenExp = "Auth token is in the incorrect format, near 'borkborkbork'";
		
		failDepGetWSmeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withWorkspace("depsave").withAuth(invalidToken),
				invalidTokenExp);
		failDepGetWSmeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withWorkspace("depsave").withAuth(badFormatToken),
				badFormatTokenExp);
		
		failDepListWs(new us.kbase.workspace.ListWorkspacesParams()
				.withAuth(invalidToken), invalidTokenExp);
		failDepListWs(new us.kbase.workspace.ListWorkspacesParams()
				.withAuth(badFormatToken), badFormatTokenExp);
		
		failDepSaveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth(invalidToken),
				invalidTokenExp);
		failDepSaveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth(badFormatToken),
				badFormatTokenExp);
		
		failDepGetObject(new us.kbase.workspace.GetObjectParams()
				.withWorkspace("depsave").withId("obj3").withAuth(invalidToken),
				invalidTokenExp);
		failDepGetObject(new us.kbase.workspace.GetObjectParams()
				.withWorkspace("depsave").withId("obj3").withAuth(badFormatToken),
				badFormatTokenExp);
		
		failDepGetObjectmeta(new us.kbase.workspace.GetObjectmetaParams()
				.withWorkspace("depsave").withId("obj3").withAuth(invalidToken),
				invalidTokenExp);
		failDepGetObjectmeta(new us.kbase.workspace.GetObjectmetaParams()
				.withWorkspace("depsave").withId("obj3").withAuth(badFormatToken),
				badFormatTokenExp);
		
		failDepListObjects(new us.kbase.workspace.ListWorkspaceObjectsParams()
				.withWorkspace("depsave").withType("thisisabadtype"),
				"Type thisisabadtype could not be split into a module and name");
		failDepListObjects(new us.kbase.workspace.ListWorkspaceObjectsParams()
				.withWorkspace("depsave").withAuth(invalidToken),
				invalidTokenExp);
		failDepListObjects(new us.kbase.workspace.ListWorkspaceObjectsParams()
				.withWorkspace("depsave").withAuth(badFormatToken),
				badFormatTokenExp);
	}
	
	@SuppressWarnings("deprecation")
	private void failDepListObjects(us.kbase.workspace.ListWorkspaceObjectsParams lwop,
			String exp)
			throws Exception {
		try {
			CLIENT1.listWorkspaceObjects(lwop);
			fail("list objs dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	@SuppressWarnings("deprecation")
	private void checkListObjectsDep(String ws, String type, Long showDeleted, String auth,
			List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long>> expected)
			throws Exception {
		Map<Long, Map<Long, Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long>>> expec =
				new HashMap<Long, Map<Long, Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long>>>();
		
		Map<Long, Set<Long>> seenSet = new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> expectedSet = new HashMap<Long, Set<Long>>();
		
		for (Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> e: expected) {
			if (!expec.containsKey(e.getE12())) {
				expec.put(e.getE12(), new HashMap<Long, Tuple12<String,String,String,Long,String,String,String,String,String,String,Map<String,String>,Long>>());
				expectedSet.put(e.getE12(), new HashSet<Long>());
			}
			expec.get(e.getE12()).put(e.getE4(), e);
			expectedSet.get(e.getE12()).add(e.getE4());
		}
		for (Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> g:
			CLIENT1.listWorkspaceObjects(new us.kbase.workspace.ListWorkspaceObjectsParams().withWorkspace(ws)
					 .withType(type).withShowDeletedObject(showDeleted).withAuth(auth))) {
			if (seenSet.containsKey(g.getE12()) &&
					seenSet.get(g.getE12()).contains(g.getE4())) {
				fail("Saw same object twice: " + g);
			}
			if (!seenSet.containsKey(g.getE12())) {
				seenSet.put(g.getE12(), new HashSet<Long>());
			}
			seenSet.get(g.getE12()).add(g.getE4());
			if (!expec.containsKey(g.getE12()) ||
					!expec.get(g.getE12()).containsKey(g.getE4())) {
				fail("listed unexpected object: " + g);
			}
			compareObjectInfoDep(g, expec.get(g.getE12()).get(g.getE4()));
		}
		assertThat("listed correct objects", seenSet, is (expectedSet));
	}

	private void compareObjectInfoDep(
			Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> got,
			Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> expec) {
		
		assertThat("name is correct", got.getE1(), is(expec.getE1()));
		assertThat("type is correct", got.getE2(), is(expec.getE2()));
		assertThat("date is correct", got.getE3(), is(expec.getE3()));
		assertThat("version is correct", got.getE4(), is(expec.getE4()));
		assertThat("command is correct", got.getE5(), is(expec.getE5()));
		assertThat("last modifier is correct", got.getE6(), is(expec.getE6()));
		assertThat("owner is correct", got.getE7(), is(expec.getE7()));
		assertThat("ws name is correct", got.getE8(), is(expec.getE8()));
		assertThat("ref is correct", got.getE9(), is(expec.getE9()));
		assertThat("chksum is correct", got.getE10(), is(expec.getE10()));
		assertThat("meta is correct", got.getE11(), is(expec.getE11()));
		assertThat("id is correct", got.getE12(), is(expec.getE12()));
	}

	@SuppressWarnings("deprecation")
	private void failDepListWs(us.kbase.workspace.ListWorkspacesParams lwp, String exp)
			throws Exception {
		try {
			CLIENT1.listWorkspaces(lwp);
			fail("get objmeta dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	private void checkWSInfoListDep(
			List<Tuple7<String, String, String, Long, String, String, Long>> got,
			List<Tuple7<String, String, String, Long, String, String, Long>> expected,
			List<Tuple7<String, String, String, Long, String, String, Long>> notexpected) {

		Map<Long, Tuple7<String, String, String, Long, String, String, Long>> expecmap = 
				new HashMap<Long, Tuple7<String, String, String, Long, String, String, Long>>();
		for (Tuple7<String, String, String, Long, String, String, Long> inf: expected) {
			expecmap.put(inf.getE7(), inf);
		}
		Set<Long> seen = new HashSet<Long>();
		Set<Long> seenexp = new HashSet<Long>();
		Set<Long> notexp = new HashSet<Long>();
		for (Tuple7<String, String, String, Long, String, String, Long> inf: notexpected) {
			notexp.add(inf.getE7());
		}
		for (Tuple7<String, String, String, Long, String, String, Long> info: got) {
			if (seen.contains(info.getE7())) {
				fail("Saw same workspace twice");
			}
			if (notexp.contains(info.getE7())) {
				fail("Got unexpected workspace id " + info.getE1());
			}
			if (!expecmap.containsKey(info.getE7())) {
				continue; // only two users so really impossible to list a controlled set of ws
				// if this is important add a 3rd user and client
			}
			seenexp.add(info.getE7());
			Tuple7<String, String, String, Long, String, String, Long> exp =
					expecmap.get(info.getE7());
			assertThat("ws name correct", info.getE1(), is(exp.getE1()));
			assertThat("user name correct", info.getE2(), is(exp.getE2()));
			assertThat("moddates correct", info.getE3(), is(exp.getE3()));
			assertThat("obj counts are 0", info.getE4(), is(exp.getE4()));
			assertThat("permission correct", info.getE5(), is(exp.getE5()));
			assertThat("global read correct", info.getE6(), is(exp.getE6()));
			assertThat("wsid correct", info.getE7(), is(exp.getE7()));
			
		}
		assertThat("got same ws ids", seenexp, is(expecmap.keySet()));
		
	}

	@SuppressWarnings("deprecation")
	private void checkDepWSMeta(
			us.kbase.workspace.GetWorkspacemetaParams gomp,
			String name, String user, String moddate, long objects, String perm,
			String globalRead, long wsid)
			throws Exception {
		Tuple7<String, String, String, Long, String, String, Long> wsmeta =
				CLIENT1.getWorkspacemeta(gomp);
		assertThat("ws name correct", wsmeta.getE1(), is(name));
		assertThat("user name correct", wsmeta.getE2(), is(user));
		assertThat("moddates correct", wsmeta.getE3(), is(moddate));
		assertThat("obj counts are 0", wsmeta.getE4(), is(objects));
		assertThat("permission correct", wsmeta.getE5(), is(perm));
		assertThat("global read correct", wsmeta.getE6(), is(globalRead));
		assertThat("wsid correct", wsmeta.getE7(), is(wsid));
		
	}

	@SuppressWarnings("deprecation")
	private void failDepGetObjectmeta(us.kbase.workspace.GetObjectmetaParams gop, String exp)
			throws Exception {
		try {
			CLIENT1.getObjectmeta(gop);
			fail("get objmeta dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}
	
	@SuppressWarnings("deprecation")
	private void failDepGetWSmeta(us.kbase.workspace.GetWorkspacemetaParams gwp, String exp)
			throws Exception {
		try {
			CLIENT1.getWorkspacemeta(gwp);
			fail("get wsmeta dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	@SuppressWarnings("deprecation")
	private void failDepGetObject(us.kbase.workspace.GetObjectParams gop, String exp)
			throws Exception {
		try {
			CLIENT1.getObject(gop);
			fail("get obj dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	@SuppressWarnings("deprecation")
	private void checkSavedObjectDep(ObjectIdentity objnames, ObjectIdentity objids,
			long id,
			String name, String type, int ver, String user, long wsid,
			String wsname, String chksum, int size, Map<String, String> meta,
			Map<String, Object> data, AuthUser auth)
			throws Exception {
		us.kbase.workspace.GetObjectOutput goo = CLIENT1.getObject(new us.kbase.workspace.GetObjectParams()
				.withId(objnames.getName()).withWorkspace(objnames.getWorkspace())
				.withInstance(objnames.getVer()));
		checkDeprecatedSaveInfo(goo.getMetadata(), id, name, type, ver, user,
				wsid, wsname, chksum, meta);
		assertThat("object data is correct", goo.getData().asClassInstance(Object.class),
				is((Object) data));
		goo = CLIENT1.getObject(new us.kbase.workspace.GetObjectParams()
				.withId(objnames.getName()).withWorkspace(objnames.getWorkspace())
				.withInstance(objnames.getVer())
				.withAuth(auth.getTokenString()));
		checkDeprecatedSaveInfo(goo.getMetadata(), id, name, type, ver, user,
				wsid, wsname, chksum, meta);
		assertThat("object data is correct", goo.getData().asClassInstance(Object.class),
				is((Object) data));
		
		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> objmeta =
				CLIENT1.getObjectmeta(new us.kbase.workspace.GetObjectmetaParams()
				.withWorkspace(objnames.getWorkspace())
				.withId(objnames.getName()).withInstance(objnames.getVer()));
		checkDeprecatedSaveInfo(objmeta, id, name, type, ver, user,
				wsid, wsname, chksum, meta);
		objmeta =
				CLIENT1.getObjectmeta(new us.kbase.workspace.GetObjectmetaParams()
				.withWorkspace(objnames.getWorkspace())
				.withId(objnames.getName()).withInstance(objnames.getVer())
				.withAuth(AUTH_USER2.getTokenString()));
		checkDeprecatedSaveInfo(objmeta, id, name, type, ver, user,
				wsid, wsname, chksum, meta);
		
		checkSavedObjects(Arrays.asList(objnames), id, name, type, ver, user, wsid, wsname, chksum, size, meta, data);
		checkSavedObjects(Arrays.asList(objids), id, name, type, ver, user, wsid, wsname, chksum, size, meta, data);
		
	}

	@SuppressWarnings("deprecation")
	private void failDepSaveObject(us.kbase.workspace.SaveObjectParams sop, String exp)
			throws Exception {
		try {
			CLIENT1.saveObject(sop);
			fail("dep save obj with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	private void checkDeprecatedSaveInfo(
			Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> info,
			long id, String name, String type, int ver, String user,
			long wsid, String wsname, String chksum,
			Map<String, String> meta) throws Exception {
		assertThat("name is correct", info.getE1(), is(name));
		assertThat("type is correct", info.getE2(), is(type));
		DATE_FORMAT.parse(info.getE3()); //should throw error if bad format
		assertThat("version is correct", (int) info.getE4().longValue(), is(ver));
		assertThat("command is correct", info.getE5(), is(""));
		assertThat("last modifier is correct", info.getE6(), is(user));
		assertThat("owner is correct", info.getE7(), is(user));
		assertThat("ws name is correct", info.getE8(), is(wsname));
		assertThat("ref is correct", info.getE9(), is(""));
		assertThat("chksum is correct", info.getE10(), is(chksum));
		assertThat("meta is correct", info.getE11(), is(meta));
		assertThat("id is correct", info.getE12(), is(id));
		
	}
	
	@Test
	public void saveBigMeta() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("bigmeta"));

		Map<String, Object> moredata = new HashMap<String, Object>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		for (int i = 0; i < 16; i++) {
			meta.put(Integer.toString(i), TEXT1000); //> 16Mb now
		}
		
		
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("bigmeta")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data)).withType(SAFE_TYPE));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withMeta(meta));
		
		try {
			CLIENT1.saveObjects(soc);
			fail("called save with too large meta");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Object 2 save error: Metadata size of 16119 is > 16000 bytes"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("bigmeta2")
					.withMeta(meta));
			fail("called createWS with too large meta");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Metadata size of 16119 is > 16000 bytes"));
		}
	}

	@Test
	public void parseRef() throws Exception {
		final String specParseRef =
				"module TestKBaseRefParsing {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"typedef structure {" +
						"reference ref1;" +
						"reference ref2;" + 
						"reference ref3;" +
						"reference ref4;" +
					"} ParseRef;" +
				"};";
		CLIENT1.requestModuleOwnership("TestKBaseRefParsing");
		administerCommand(CLIENT2, "approveModRequest", "module", "TestKBaseRefParsing");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(specParseRef)
			.withNewTypes(Arrays.asList("ParseRef")));
		String type ="TestKBaseRefParsing.ParseRef-0.1";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("parseref"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("parseref");
		long wsid = CLIENT1.getWorkspaceInfo(wsi).getE1();
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("fubar", "foo");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("parseref")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE));
		CLIENT1.saveObjects(soc);
		data.clear();
		Set<String> expectedRefs = new HashSet<String>();
		data.put("ref1", "kb|ws." + wsid + ".obj.1");
		expectedRefs.add(wsid + "/1/3");
		data.put("ref2", "kb|ws." + wsid + ".obj.1.ver.2");
		expectedRefs.add(wsid + "/1/2");
		data.put("ref3", "kb|ws." + wsid + ".obj.2");
		expectedRefs.add(wsid + "/2/1");
		data.put("ref4", "kb|ws." + wsid + ".obj.2.ver.1");
		expectedRefs.add(wsid + "/2/1");
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(type));
		CLIENT1.saveObjects(soc);
		ObjectData od = CLIENT1.getObjects(Arrays.asList(
				new ObjectIdentity().withWsid(wsid).withName("auto3")))
				.get(0);
		Map<String, String> refs = od.getData().asInstance();
		assertThat("correct ref parse/rewrite", refs.get("ref1"), is(wsid + "/1/3"));
		assertThat("correct ref parse/rewrite", refs.get("ref2"), is(wsid + "/1/2"));
		assertThat("correct ref parse/rewrite", refs.get("ref3"), is(wsid + "/2/1"));
		assertThat("correct ref parse/rewrite", refs.get("ref4"), is(wsid + "/2/1"));
		assertThat("correct refs returned", new HashSet<String>(od.getRefs()),
				is(expectedRefs));
	}
	
	@Test
	public void deleteUndelete() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("delundel")
				.withDescription("foo"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("delundel");
		long wsid = CLIENT1.getWorkspaceInfo(wsi).getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("delundel")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		CLIENT1.saveObjects(soc);
		List<ObjectIdentity> loi = Arrays.asList(new ObjectIdentity()
				.withRef("delundel/myname"));
		checkData(loi, data);
		CLIENT1.deleteObjects(loi);
		
		failGetObjects(loi, "Object 1 (name myname) in workspace " + wsid + " has been deleted");

		CLIENT1.undeleteObjects(loi);
		checkData(loi, data);
		CLIENT1.deleteWorkspace(wsi);
		
		failGetObjects(loi, "Object myname cannot be accessed: Workspace delundel is deleted");

		try {
			CLIENT1.getWorkspaceDescription(wsi);
			fail("got desc from deleted WS");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Workspace delundel is deleted"));
		}
		CLIENT1.undeleteWorkspace(wsi);
		checkData(loi, data);
		assertThat("can get description", CLIENT1.getWorkspaceDescription(wsi),
				is("foo"));
		CLIENT1.deleteObjects(loi);
		
		failGetObjects(loi, "Object 1 (name myname) in workspace " + wsid + " has been deleted");

		CLIENT1.saveObjects(soc);
		checkData(loi, data);
	}
	
	@Test
	public void copyRevert() throws Exception {
		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("copyrev")).getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("copyrev")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> objs =
				CLIENT1.saveObjects(soc);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> copied =
				CLIENT1.copyObject(new CopyObjectParams().withFrom(new ObjectIdentity().withRef("copyrev/myname"))
				.withTo(new ObjectIdentity().withWsid(wsid).withName("myname2")));
		compareObjectInfoAndData(objs.get(1), copied, "copyrev", wsid, "myname2", 2L, 2);
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> copystack =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "copyrev", wsid, "myname2", 2L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "copyrev", wsid, "myname2", 2L, 2);
		
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> rev =
				CLIENT1.revertObject(new ObjectIdentity().withWorkspace("copyrev").withObjid(2L)
				.withVer(1L));
		compareObjectInfoAndData(objs.get(0), rev, "copyrev", wsid, "myname2", 2L, 3);
		copystack = CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "copyrev", wsid, "myname2", 2L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "copyrev", wsid, "myname2", 2L, 2);
		compareObjectInfoAndData(objs.get(0), copystack.get(2), "copyrev", wsid, "myname2", 2L, 3);
		
		CopyObjectParams cpo = new CopyObjectParams().withFrom(new ObjectIdentity().withRef("copyrev/myname"))
				.withTo(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		cpo.setAdditionalProperties("foo", "bar");
		try {
			CLIENT1.copyObject(cpo);
			fail("copied with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("Unexpected arguments in CopyObjectParams: foo"));
		}
	}
	
	@Test
	public void cloneWorkspace() throws Exception {
		String source = "clonesource";
		WorkspaceIdentity wssrc = new WorkspaceIdentity().withWorkspace(source);
		
		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(source)).getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace(source)
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));
		
		CLIENT1.saveObjects(soc);
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("Bowhale", "the avenger");
		
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.cloneWorkspace(new CloneWorkspaceParams().withDescription("a desc")
				.withGlobalread("r").withWorkspace("newclone").withWsi(wssrc)
				.withMeta(meta));
		checkWS(wsinfo, wsinfo.getE1(), wsinfo.getE4(), "newclone", USER1, 1, "a", "r", "unlocked", "a desc", meta);
		
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> objs =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname"));
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> copystack =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsinfo.getE1()).withName("myname"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "newclone", wsinfo.getE1(), "myname", 1L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "newclone", wsinfo.getE1(), "myname", 1L, 2);
		
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo2 =
				CLIENT1.cloneWorkspace(new CloneWorkspaceParams().withWorkspace("newclone2").withWsi(wssrc));
		checkWS(wsinfo2, wsinfo2.getE1(), wsinfo2.getE4(), "newclone2", USER1, 1, "a", "n", "unlocked", null, MT_META);
		
		
		CloneWorkspaceParams cpo = new CloneWorkspaceParams().withWsi(new WorkspaceIdentity().withWorkspace("newclone"))
				.withWorkspace("fake");
		cpo.setAdditionalProperties("foo", "bar");
		try {
			CLIENT1.cloneWorkspace(cpo);
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("Unexpected arguments in CloneWorkspaceParams: foo"));
		}
		
		cpo = new CloneWorkspaceParams().withWsi(new WorkspaceIdentity().withWorkspace("newclone"))
				.withWorkspace("fake");
		try {
			CLIENT1.cloneWorkspace(cpo.withGlobalread("w"));
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
	}
	
	@Test
	public void lockWorkspace() throws Exception {
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("Go to Spain", "there are millions of them");
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("lock");
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("lock")
						.withMeta(meta));
		long wsid = info.getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("lock")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));
		
		CLIENT1.saveObjects(soc);
		info = CLIENT1.getWorkspaceInfo(wsi); //saving changes the date
		
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> lockinfo =
				CLIENT1.lockWorkspace(wsi);
		checkWS(lockinfo, wsid, info.getE4(), "lock", USER1, 1, "a", "n", "locked", null, meta);
		try {
			CLIENT1.setWorkspaceDescription(new SetWorkspaceDescriptionParams().withDescription("foo")
					.withWorkspace("lock"));
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("lock")
				.withNewPermission("r"));
		checkWS(CLIENT1.getWorkspaceInfo(wsi), wsid, info.getE4(), "lock",
				USER1, 1, "a", "r", "published", null, meta);
	}
	
	@Test
	public void renameObject() throws Exception {
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("renameObj"));
		long wsid = wsinfo.getE1();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("renameObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("myname")));
		CLIENT1.saveObjects(soc);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info =
				CLIENT1.renameObject(new RenameObjectParams().withNewName("mynewname")
				.withObj(new ObjectIdentity().withRef("renameObj/1")));
		checkInfo(info, 1, "mynewname", SAFE_TYPE, 1, USER1, wsid, "renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null);
		info = CLIENT1.getObjectInfoNew(new GetObjectInfoNewParams().withObjects(
				Arrays.asList(new ObjectIdentity().withWorkspace("renameObj").withObjid(1L)))).get(0);
		checkInfo(info, 1, "mynewname", SAFE_TYPE, 1, USER1, wsid, "renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null);
		RenameObjectParams rop = new RenameObjectParams().withNewName("mynewname2")
				.withObj(new ObjectIdentity().withRef("renameObj/1"));
		rop.setAdditionalProperties("foo", "bar");
		failObjRename(rop, "Unexpected arguments in RenameObjectParams: foo");
		failObjRename(new RenameObjectParams().withNewName("foo")
				.withObj(new ObjectIdentity().withName("foo")),
				"Must provide one and only one of workspace name (was: null) or id (was: null)");
	}

	@Test
	public void renameWorkspace() throws Exception {
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("pimhole", "semprini");
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("renameWS")
						.withMeta(meta));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo2 =
				CLIENT1.renameWorkspace(new RenameWorkspaceParams().withWsi(
				new WorkspaceIdentity().withWorkspace("renameWS")).withNewName("newrenameWS"));
		checkWS(wsinfo2, wsinfo.getE1(), wsinfo2.getE4(), "newrenameWS", USER1,
				0, "a", "n", "unlocked", null, meta);
		wsinfo2 = CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("newrenameWS"));
		checkWS(wsinfo2, wsinfo.getE1(), wsinfo2.getE4(), "newrenameWS", USER1,
				0, "a", "n", "unlocked", null, meta);
		RenameWorkspaceParams rwp = new RenameWorkspaceParams()
				.withWsi(new WorkspaceIdentity().withWorkspace("newrenameWS"))
				.withNewName("foo");
		rwp.setAdditionalProperties("foo", "bar");
		failWSRename(rwp, "Unexpected arguments in RenameWorkspaceParams: foo");
		failWSRename(new RenameWorkspaceParams().withWsi(new WorkspaceIdentity()
				.withWorkspace("newrenameWS")), "Workspace name cannot be null or the empty string");
	}

	@Test
	public void setGlobalPermission() throws Exception {
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("setglobal"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("setglobal");
		assertThat("globalread is none", wsinfo.getE7(), is("n"));
		try {
			CLIENT2.getWorkspaceDescription(wsi);
			fail("got workspace desc w/o access");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("User " + USER2 + " may not read workspace setglobal"));
		}
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("setglobal")
				.withNewPermission("r"));
		CLIENT2.getWorkspaceDescription(wsi);
		assertThat("globalread is r", CLIENT1.getWorkspaceInfo(wsi).getE7(), is("r"));
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("setglobal")
				.withNewPermission("n"));
		assertThat("globalread is r", CLIENT1.getWorkspaceInfo(wsi).getE7(), is("n"));
		SetGlobalPermissionsParams sgpp = new SetGlobalPermissionsParams()
				.withWorkspace("setglobal").withNewPermission("r");
		sgpp.setAdditionalProperties("bar", "foo");
		failSetGlobalPerm(sgpp, "Unexpected arguments in SetGlobalPermissionsParams: bar");
		SetGlobalPermissionsParams sgppgen = new SetGlobalPermissionsParams()
				.withWorkspace("setglobal");
		failSetGlobalPerm(sgppgen.withNewPermission("w"),
				"Global permissions cannot be greater than read");
		failSetGlobalPerm(sgppgen.withNewPermission("z"),
				"No such permission: z");
		failSetGlobalPerm(sgppgen.withNewPermission("r").withId(wsinfo.getE1()),
				"Must provide one and only one of workspace name (was: setglobal) or id (was: " +
				wsinfo.getE1() + ")");
	}

	@Test
	public void hiddenObjects() throws Exception {
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("hideObj"));
		long wsid = wsinfo.getE1();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("hideObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("unhidden")));
		ObjectIdentity o1 = new ObjectIdentity().withRef("hideObj/1");
		CLIENT1.saveObjects(soc);
		soc = new SaveObjectsParams().withWorkspace("hideObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("hidden").withHidden(1L)));
		ObjectIdentity o2 = new ObjectIdentity().withWorkspace("hideObj").withName("hidden");
		CLIENT1.saveObjects(soc);

		Set<Long> expected = new HashSet<Long>();
		expected.add(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		expected.add(2L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid)).withShowHidden(1L)), expected);
		CLIENT1.unhideObjects(Arrays.asList(o2));
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		CLIENT1.hideObjects(Arrays.asList(o1));
		expected.remove(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		expected.add(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid)).withShowHidden(1L)), expected);
		
		ObjectIdentity badoi = new ObjectIdentity().withWorkspace("hideObj").withName("hidden");
		badoi.setAdditionalProperties("urg", "bleah");
		
		failHideUnHide(badoi, "Error on ObjectIdentity #1: Unexpected arguments in ObjectIdentity: urg");
		failHideUnHide(new ObjectIdentity().withWorkspace("hideObj"),
				"Error on ObjectIdentity #1: Must provide one and only one of object name (was: null) or id (was: null)");
		failHideUnHide(new ObjectIdentity().withWorkspace("hideObj").withName("wootwoot"),
				"No object with name wootwoot exists in workspace " + wsid);
	}

	@Test
	public void listWorkspaceInfo() throws Exception {
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("credulous", "git");
		
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("Flanders", "pidgeon murderer");
		
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> std =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("liststd")
						.withMeta(meta));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listglobalread")
				.withGlobalread("r"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> globalread =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listglobalread"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> deleted =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listdeleted"));
		CLIENT1.deleteWorkspace(new WorkspaceIdentity().withWorkspace("listdeleted"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listwrite")
				.withMeta(meta2));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listwrite")
				.withNewPermission("w").withUsers(Arrays.asList(USER1)));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> write =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listwrite"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listadmin"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listadmin")
				.withNewPermission("a").withUsers(Arrays.asList(USER1)));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> admin =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listadmin"));
		
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()),
				Arrays.asList(std, globalread, write, admin), Arrays.asList(deleted));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(0L).withShowDeleted(0L).withShowOnlyDeleted(0L)),
				Arrays.asList(std, globalread, write, admin), Arrays.asList(deleted));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(0L).withShowDeleted(0L).withShowOnlyDeleted(0L)
				.withOwners(new ArrayList<String>())),
				Arrays.asList(std, globalread, write, admin), Arrays.asList(deleted));
		
		//filter on meta
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withMeta(meta)),
				Arrays.asList(std), Arrays.asList(deleted,  globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withMeta(meta2)),
				Arrays.asList(write), Arrays.asList(std, deleted, globalread, admin));
		
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withOwners(Arrays.asList(USER1))),
				Arrays.asList(std), Arrays.asList(deleted));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withOwners(Arrays.asList(USER2))),
				Arrays.asList(globalread, write, admin), Arrays.asList(deleted));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withOwners(Arrays.asList(USER1, USER2))),
				Arrays.asList(std, globalread, write, admin), Arrays.asList(deleted));
		
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
					.withPerm("n")),
				Arrays.asList(std, globalread, write, admin), Arrays.asList(deleted));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withPerm("r")),
				Arrays.asList(std, globalread, write, admin), Arrays.asList(deleted));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withPerm("w")),
				Arrays.asList(std, write, admin), Arrays.asList(deleted, globalread));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withPerm("a")),
				Arrays.asList(std, admin), Arrays.asList(deleted, globalread, write));
		
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(1L)),
				Arrays.asList(std, write, admin), Arrays.asList(deleted, globalread));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(1L).withShowDeleted(0L)),
				Arrays.asList(std, write, admin), Arrays.asList(deleted, globalread));
		
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(1L).withShowDeleted(1L)),
				Arrays.asList(std, deleted, write, admin), Arrays.asList(globalread));
		
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withShowDeleted(1L)),
				Arrays.asList(std, deleted, globalread, write, admin),
				new ArrayList<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>());
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(0L).withShowDeleted(1L)),
				Arrays.asList(std, deleted, globalread, write, admin), 
				new ArrayList<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>());
		
		checkWSInfoList(CLIENT2.listWorkspaceInfo(new ListWorkspaceInfoParams()),
				Arrays.asList(CLIENT2.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listglobalread")),
						CLIENT2.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listwrite")),
						CLIENT2.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listadmin"))),
				Arrays.asList(std, deleted));
		
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(0L).withShowOnlyDeleted(1L)),
				Arrays.asList(deleted), Arrays.asList(std, globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(1L).withShowOnlyDeleted(1L)),
				Arrays.asList(deleted), Arrays.asList(std, globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withShowDeleted(1L).withShowOnlyDeleted(1L)),
				Arrays.asList(deleted), Arrays.asList(std, globalread, write, admin));
		
		ListWorkspaceInfoParams lwip = new ListWorkspaceInfoParams();
		lwip.setAdditionalProperties("booga", "booga1");
		try {
			CLIENT1.listWorkspaceInfo(lwip);
			fail("list ws with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Unexpected arguments in ListWorkspaceInfoParams: booga"));
		}
	}
	
	@Test
	public void listWorkspaceInfoByDate() throws Exception {
		List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> mt =
				new ArrayList<Tuple9<Long,String,String,String,Long,String,String,String,Map<String,String>>>();
		
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> w1 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listWSByDate1"));
		Thread.sleep(2000);
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> w2 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listWSByDate2"));
		Thread.sleep(2000);
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> w3 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listWSByDate3"));
		String beforeall = subSec(w1.getE4()); //max res is 1s
		String afterall = addSec(w3.getE4());
		
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)),
				Arrays.asList(w1, w2, w3), mt, true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfter(beforeall).withBefore(afterall)),
				Arrays.asList(w1, w2, w3), mt, true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfter(afterall).withBefore(beforeall)),
				mt, Arrays.asList(w1, w2, w3), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfter(addSec(w1.getE4()))),
				Arrays.asList(w2, w3), Arrays.asList(w1), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withBefore(subSec(w3.getE4()))),
				Arrays.asList(w1, w2), Arrays.asList(w3), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfter(addSec(w1.getE4())).withBefore(subSec(w3.getE4()))),
				Arrays.asList(w2), Arrays.asList(w1, w3), true);
		
		failListWorkspaceByDate("crappy date", "Unparseable date: Invalid format: \"crappy date\"");
	}
	
	@Test
	public void listObjectsAndHistory() throws Exception {
		CLIENT1.requestModuleOwnership("AnotherModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "AnotherModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams().withDryrun(0L)
			.withNewTypes(Arrays.asList("AType"))
			.withSpec(
					"module AnotherModule {" +
						"/* @optional thing */" +
						"typedef structure {" +
						"string thing;" +
						"} AType;" +
					"};")
			);
		CLIENT1.releaseModule("AnotherModule");
		CLIENT1.requestModuleOwnership("AnotherModule2");
		administerCommand(CLIENT2, "approveModRequest", "module", "AnotherModule2");
		CLIENT1.registerTypespec(new RegisterTypespecParams().withDryrun(0L)
			.withNewTypes(Arrays.asList("AType"))
			.withSpec(
					"module AnotherModule2 {" +
						"/* @optional thing */" +
						"typedef structure {" +
						"string thing;" +
						"} AType;" +
					"};")
			);
		CLIENT1.releaseModule("AnotherModule2");
		
		String anotherType = "AnotherModule.AType-0.1";
		String anotherType2 = "AnotherModule2.AType-0.1";
		
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info1 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjs1"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info2 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjs2"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjsread"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listObjsread")
				.withNewPermission("w").withUsers(Arrays.asList(USER1)));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjswrite"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listObjswrite")
				.withNewPermission("w").withUsers(Arrays.asList(USER1)));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjsadmin"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listObjsadmin")
				.withNewPermission("a").withUsers(Arrays.asList(USER1)));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjsGlobal")
				.withGlobalread("r"));
		
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("meta1", "1");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "2");
		
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> std1 =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("listObjs1")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta).withType(anotherType).withName("std")))).get(0);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> std2 =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("listObjs1")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta2).withType(anotherType2).withName("std")))).get(0);
		
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> hidden =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("listObjs2")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta).withType(anotherType).withName("hidden").withHidden(1L)))).get(0);
		
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> deleted =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("listObjs2")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta2).withType(anotherType).withName("deleted")))).get(0);
		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withWorkspace("listObjs2").withName("deleted")));
		
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> readable =
				CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("listObjsread")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta).withType(anotherType).withName("write")))).get(0);
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listObjsread")
				.withNewPermission("r").withUsers(Arrays.asList(USER1)));
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> writeable =
				CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("listObjswrite")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta2).withType(anotherType).withName("write")))).get(0);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> adminable =
				CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("listObjsadmin")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta2).withType(anotherType).withName("admin")))).get(0);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> global =
				CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("listObjsGlobal")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta).withType(anotherType).withName("global")))).get(0);
		
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, hidden, deleted), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, null, 1L, 1L, 0L,
				Arrays.asList(std1, std2, hidden, deleted), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 1L, 1L, 1L, 0L,
				Arrays.asList(deleted), false);
		checkListObjects(Arrays.asList("listObjs1"), new ArrayList<Long>(), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2), false);
		checkListObjects(new ArrayList<String>(), Arrays.asList(info1.getE1()), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2), false);
		checkListObjects(Arrays.asList("listObjs2"), new ArrayList<Long>(), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(hidden, deleted), false);
		checkListObjects(new ArrayList<String>(), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(hidden, deleted), false);
		checkListObjects(Arrays.asList("listObjs1", "listObjs2"), new ArrayList<Long>(), null, null, null, null, 1L, 1L, 0L, 1L, 0L, 0L,
				Arrays.asList(std1, std2, hidden, deleted), true);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		
		//exclude global ws
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null, null, null, 1L, 1L, 0L, 1L, 1L, 1L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable), false);
		
		//user filtering
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null,
				new ArrayList<String>(), null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null,
				Arrays.asList(USER1, USER2), null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null,
				Arrays.asList(USER1), null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted), false);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null,
				Arrays.asList(USER2), null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(readable, writeable, adminable, global), false);
		
		//perms testing
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, "n", null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, "r", null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, "w", null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, writeable, adminable), false);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, "a", null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, adminable), false);
		
		//meta data testing
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null, null,
				new HashMap<String, String>(), 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null, null,
				meta, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, readable, global), false);
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType, null, null,
				meta2, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(deleted, writeable, adminable), false);
		
		checkListObjects(new ArrayList<String>(), new ArrayList<Long>(), anotherType2, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std2), false);
		checkListObjects(new ArrayList<String>(), Arrays.asList(info2.getE1(), info1.getE1()), null, null, null, null, null, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, deleted), false);
		checkListObjects(Arrays.asList("listObjs2"), Arrays.asList(info1.getE1()), null, null, null, null, 0L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, deleted), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, null, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, hidden), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 0L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, hidden), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 0L, null, 1L, 0L,
				Arrays.asList(deleted, std2, hidden), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 0L, 0L, 1L, 0L,
				Arrays.asList(deleted, std2, hidden), false);
		
		failListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), "Foo", null, null, 1L, 1L, 1L, 1L,
				"Type Foo could not be split into a module and name");
		failListObjects(Arrays.asList("listObjs1"), Arrays.asList(-1L), null, null, null, 1L, 1L, 1L, 1L,
				"Workspace id must be > 0");
		failListObjects(Arrays.asList("foo:bar:listObjs1"), Arrays.asList(1L), null, null, null, 1L, 1L, 1L, 1L,
				"Workspace name foo:bar:listObjs1 may only contain one : delimiter");
		failListObjects(Arrays.asList("listObjs1fake"), Arrays.asList(info2.getE1()), anotherType, null, null, 1L, 1L, 1L, 1L,
				"No workspace with name listObjs1fake exists");
		failListObjects(new ArrayList<String>(), new ArrayList<Long>(), null, null, null, 1L, 1L, 1L, 1L,
				"At least one filter must be specified.");
		failListObjects(Arrays.asList("listObjs1"), Arrays.asList(1L), null, "x", null, 1L, 1L, 1L, 1L,
				"No such permission: x");
		meta.put("this should", "force a fail");
		failListObjects(Arrays.asList("listObjs1"), Arrays.asList(1L), null, null, meta, 1L, 1L, 1L, 1L,
				"Only one metadata spec allowed");
		
		compareObjectInfo(CLIENT1.getObjectHistory(
				new ObjectIdentity().withRef("listObjs1/std")), 
						Arrays.asList(std1, std2));
		compareObjectInfo(CLIENT1.getObjectHistory(
				new ObjectIdentity().withRef("listObjs2/hidden/1")), 
						Arrays.asList(hidden));
		
		try {
			CLIENT1.getObjectHistory(new ObjectIdentity().withRef("listObjs1/hidden/1/3"));
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Illegal number of separators / in object reference listObjs1/hidden/1/3"));
		}
	}
	
	@Test
	public void listObjectsPagination() throws Exception {
		String ws = "pagination";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws));
		
		List<ObjectSaveData> objs = new LinkedList<ObjectSaveData>();
		for (int i = 0; i < 200; i++) {
			objs.add(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
					.withType(SAFE_TYPE));
		}
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws)
				.withObjects(objs));
		
		//this depends on the natural sort order of mongo
		checkObjectPagination(ws, null, null, 1, 200);
		checkObjectPagination(ws, -1L, 0L, 1, 200);
		checkObjectPagination(ws, -1L, 50L, 1, 50);
		checkObjectPagination(ws, 100L, 50L, 101, 150);
		checkObjectPagination(ws, 100L, 100L, 101, 200);
		checkObjectPagination(ws, 150L, 100L, 151, 200);
		checkObjectPagination(ws, 150L, 1L, 151, 151);
		checkObjectPagination(ws, 200L, -1L, 2, 1); //hack
		
		failListObjects(Arrays.asList(ws), null, null, null, null, 0L, 0L,
				0L, 0L, 4000000000L, 1L, "Skip can be no greater than 2147483647");
		failListObjects(Arrays.asList(ws), null, null, null, null, 0L, 0L,
				0L, 0L, 1L, 4000000000L, "Limit can be no greater than 2147483647");
	}
	
	@Test
	public void listObjectsByDate() throws Exception {
		ArrayList<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> mt =
				new ArrayList<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>>();
		String ws = "listObjsByDate";
		UObject d = new UObject(new HashMap<String, String>());
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws));
		SaveObjectsParams p = new SaveObjectsParams().withWorkspace(ws)
				.withObjects(Arrays.asList(new ObjectSaveData().withData(d)
						.withType(SAFE_TYPE).withName("o1")));
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> o1 =
				CLIENT1.saveObjects(p).get(0);
		p.getObjects().get(0).setName("o2");
		Thread.sleep(2000);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> o2 =
				CLIENT1.saveObjects(p).get(0);
		p.getObjects().get(0).setName("o3");
		Thread.sleep(2000);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> o3 =
				CLIENT1.saveObjects(p).get(0);
		String beforeall = subSec(o1.getE4()); //max res is 1s
		String afterall = addSec(o3.getE4());
		
		ListObjectsParams lp = new ListObjectsParams().withWorkspaces(Arrays.asList(ws))
				.withIncludeMetadata(1L);
		compareObjectInfo(CLIENT1.listObjects(lp), Arrays.asList(o1, o2, o3), false);
		lp = lp.withAfter(beforeall).withBefore(afterall);
		compareObjectInfo(CLIENT1.listObjects(lp), Arrays.asList(o1, o2, o3), false);
		lp = lp.withAfter(afterall).withBefore(beforeall);
		compareObjectInfo(CLIENT1.listObjects(lp), mt, false);
		lp = lp.withAfter(addSec(o1.getE4())).withBefore(null);
		compareObjectInfo(CLIENT1.listObjects(lp), Arrays.asList(o2, o3), false);
		lp = lp.withAfter(null).withBefore(subSec(o3.getE4()));
		compareObjectInfo(CLIENT1.listObjects(lp), Arrays.asList(o1, o2), false);
		lp = lp.withAfter(addSec(o1.getE4())).withBefore(subSec(o3.getE4()));
		compareObjectInfo(CLIENT1.listObjects(lp), Arrays.asList(o2), false);
		
		failListObjectsByDate("crappy obj date", "Unparseable date: Invalid format: \"crappy obj date\"");
	}
	
	@Test
	public void getObjectSubset() throws Exception {
		/* note most tests are performed at the same time as getObjects, so
		 * only issues specific to subsets are tested here
		 */
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info1 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("subdata"));
		
		String strdata = 
				"{\"foobar\":\"somestuff\"," +
				 "\"map\":{\"id1\":{\"id\":1," +
								   "\"thing\":\"foo\"}," +
						  "\"id2\":{\"id\":2," +
								   "\"thing\":\"foo2\"}," +
						  "\"id3\":{\"id\":3," +
								   "\"thing\":\"foo3\"}" +
						  "}" +
				"}";
		String md5 = DigestUtils.md5Hex(strdata);
		assertThat("md5 correct", md5, is("06c2ae8f77ad36e262bca7b186c944ec"));
		
		Map<String, Object> data = createData( // intentionally unsorted
				"{\"map\": {\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id2\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}," +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}," +
				" \"foobar\": \"somestuff\"" +
				"}"
				);
		
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("subdata")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("std")))).get(0);
		
		ObjectData od = CLIENT1.getObjectSubset(Arrays.asList(
				new SubObjectIdentity().withRef("subdata/1")
				.withIncluded(Arrays.asList("/map/id1", "/map/id3")))).get(0);
		Map<String, Object> expdata = createData(
				"{\"map\": {\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}" +
				"}"
				);
		checkData(od, 1, "std", SAFE_TYPE, 1, USER1, info1.getE1(), "subdata",
				md5, 119, new HashMap<String, String>(),
				expdata);
		
		try {
			CLIENT1.getObjectSubset(Arrays.asList(
					new SubObjectIdentity().withRef("subdata/1")
					.withIncluded(Arrays.asList("/map/id1", "/map/id4")))).get(0);
			fail("listed objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Malformed selection string, cannot get 'id4', at: /map/id4"));
		}
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("subdata").withNewPermission("n"));
	}
	
	@Test
	public void listReferencingObjects() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("referingobjs"));
		
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("referingobjs")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("std")))).get(0);
		
		Map<String, Object> refdata = new HashMap<String, Object>();
		refdata.put("ref", "referingobjs/std/1");
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> ref =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("referingobjs")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(refdata))
				.withType(REF_TYPE).withName("ref")))).get(0);
		
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> prov =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("referingobjs")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("prov").withProvenance(Arrays.asList(
						new ProvenanceAction().withInputWsObjects(Arrays.asList("referingobjs/std/1"))))))).get(0);
		
		List<ObjectIdentity> loi = Arrays.asList(new ObjectIdentity().withRef("referingobjs/std/1"));
		List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>>> retrefs =
				CLIENT1.listReferencingObjects(loi);
		
		assertThat("one obj list returned", retrefs.size(), is(1));
		assertThat("two refs returned", retrefs.get(0).size(), is(2));
		compareObjectInfo(retrefs.get(0), Arrays.asList(ref, prov), false);
		List<Long> refcnts = CLIENT1.listReferencingObjectCounts(loi);
		assertThat("got correct refcounts", refcnts, is(Arrays.asList(2L)));
	}
	
	@Test
	public void getReferencedObjects() throws Exception {
		
		long wsid1 = CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("referenced")).getE1();
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("referenced")
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		long wsid2 = CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("referencedPriv")).getE1();
		
		Map<String, Object> data1 = createData("{\"foobar\": \"somestuff\"}");
		Map<String, Object> data2 = createData("{\"foobar\": \"somestuff2\"}");
		
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("referencedPriv")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data1))
				.withType(SAFE_TYPE).withName("one"))));
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("referencedPriv")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data2))
				.withType(SAFE_TYPE).withName("two"))));
		
		Map<String, Object> refdata = createData("{\"ref\": \"referencedPriv/one\"}");
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("referenced")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(refdata))
				.withType(REF_TYPE).withName("ref"))));
		
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("referenced")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("prov").withProvenance(Arrays.asList(
						new ProvenanceAction().withInputWsObjects(Arrays.asList("referencedPriv/two")))))));
		
		List<ObjectData> exp = CLIENT2.getObjects(Arrays.asList(
				new ObjectIdentity().withRef("referencedPriv/one"),
				new ObjectIdentity().withRef("referencedPriv/two")));
		
		getReferencedObjectsCheckData(exp);
		
		
		failGetReferencedObjects(null, "refChains may not be null");
		failGetReferencedObjects(Arrays.asList(null, Arrays.asList(null, new ObjectIdentity().withRef("referenced/ref"))),
				"Error on object chain #1: The object identifier list cannot be null");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				new ObjectIdentity().withRef("referencedPriv/one")), null),
				"Error on object chain #2: The object identifier list cannot be null");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				new ObjectIdentity().withRef("referencedPriv/one")), new ArrayList<ObjectIdentity>()),
				"Error on object chain #2: No object identifiers provided");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"))),
				"Error on object chain #1: The minimum size of a reference chain is 2 ObjectIdentities");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"), null)),
				"Error on object chain #1: Error on ObjectIdentity #2: ObjectIdentities cannot be null");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref").withName("foo"),
				new ObjectIdentity().withRef("referenced/ref"))),
				"Error on object chain #1: Error on ObjectIdentity #1: Object reference referenced/ref provided; cannot provide any other means of identifying an object. Object name: foo");
		
		ObjectIdentity oi = new ObjectIdentity().withRef("saveget/1");
		oi.setAdditionalProperties("foo", "bar");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				oi)), "Error on object chain #1: Error on ObjectIdentity #2: Unexpected arguments in ObjectIdentity: foo");
		
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withWorkspace("referencedPriv").withName("one"),
				new ObjectIdentity().withRef("referencedPriv/two"))), "Object one cannot be accessed: User " + USER1 + " may not read workspace referencedPriv");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withWorkspace("referenced").withName("ref"),
				new ObjectIdentity().withRef("referencedPrivfake/two"))), "Object two cannot be accessed: No workspace with name referencedPrivfake exists");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withWorkspace("referenced").withName("ref"),
				new ObjectIdentity().withRef("referencedPriv/three"))), "No object with name three exists in workspace " + wsid2);

		CLIENT2.deleteObjects(Arrays.asList(new ObjectIdentity().withRef("referencedPriv/one"),
				new ObjectIdentity().withRef("referencedPriv/two")));
		CLIENT2.deleteWorkspace(new WorkspaceIdentity().withWorkspace("referencedPriv"));
		getReferencedObjectsCheckData(exp);
		
		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withRef("referenced/ref")));
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				new ObjectIdentity().withRef("referencedPriv/one"))),
				"Object 1 (name ref) in workspace " + wsid1 + " has been deleted");
		CLIENT1.deleteWorkspace(new WorkspaceIdentity().withWorkspace("referenced"));
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				new ObjectIdentity().withRef("referencedPriv/one"))),
				"Object ref cannot be accessed: Workspace referenced is deleted");
	}

	@Test
	public void adminAddRemoveList() throws Exception {
		checkAdmins(CLIENT2, Arrays.asList(USER2));
		failAdmin(CLIENT1, "{\"command\": \"listAdmins\"}", "User " + USER1 + " is not an admin");
		failAdmin(CLIENT2, "{\"command\": \"listAdmin\"}", "I don't know how to process the command: listAdmin");
		failAdmin(CLIENT2, "{\"command\": \"addAdmin\"," +
						   " \"user\": \"thisisnotavalidkbaseuserihopeorthistestwillfail\"}",
				"thisisnotavalidkbaseuserihopeorthistestwillfail is not a valid KBase user");
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"addAdmin\"," +
				" \"user\": \"" + USER1 + "\"}")));
		
		checkAdmins(CLIENT2, Arrays.asList(USER1, USER2));
		CLIENT1.administer(new UObject(createData(
				"{\"command\": \"removeAdmin\"," +
				" \"user\": \"" + USER1 + "\"}")));
		failAdmin(CLIENT1, "{\"command\": \"listAdmins\"}", "User " + USER1 + " is not an admin");
		checkAdmins(CLIENT2, Arrays.asList(USER2));
		
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"addAdmin\"," +
				" \"user\": \"" + USER1 + "\"}")));
		CLIENT1.administer(new UObject(createData(
				"{\"command\": \"removeAdmin\"," +
				" \"user\": \"" + USER2 + "\"}")));
		failAdmin(CLIENT2, "{\"command\": \"listAdmins\"}", "User " + USER2 + " is not an admin");
		checkAdmins(CLIENT1, Arrays.asList(USER1));
		CLIENT1.administer(new UObject(createData(
				"{\"command\": \"addAdmin\"," +
				" \"user\": \"" + USER2 + "\"}")));
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"removeAdmin\"," +
				" \"user\": \"" + USER1 + "\"}")));
		checkAdmins(CLIENT2, Arrays.asList(USER2));
	}
	
	@Test
	public void adminModRequest() throws Exception {
		Map<String, String> mod2owner = new HashMap<String, String>();
		checkModRequests(mod2owner);
		CLIENT1.requestModuleOwnership("SomeMod");
		CLIENT1.requestModuleOwnership("SomeMod2");
		failAdmin(CLIENT1, "{\"command\": \"approveModRequest\"," +
				   " \"module\": \"SomeMod\"}", "User " + USER1 + " is not an admin");
		mod2owner.put("SomeMod", USER1);
		mod2owner.put("SomeMod2", USER1);
		checkModRequests(mod2owner);
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"approveModRequest\"," +
				" \"module\": \"SomeMod\"}")));
		mod2owner.remove("SomeMod");
		checkModRequests(mod2owner);
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"denyModRequest\"," +
				" \"module\": \"SomeMod2\"}")));
		mod2owner.remove("SomeMod2");
		checkModRequests(mod2owner);
		
		failAdmin(CLIENT2, "{\"command\": \"approveModRequest\"," +
						   " \"module\": \"SomeMod\"}", "There is no request for module SomeMod");
		failAdmin(CLIENT2, "{\"command\": \"approveModRequest\"," +
				   " \"module\": \"SomeMod3\"}", "There is no request for module SomeMod3");
		failAdmin(CLIENT2, "{\"command\": \"denyModRequest\"," +
				   " \"module\": \"SomeMod\"}", "There is no request for module SomeMod");
		failAdmin(CLIENT2, "{\"command\": \"denyModRequest\"," +
				   " \"module\": \"SomeMod3\"}", "There is no request for module SomeMod3");
		
		CLIENT1.registerTypespec(new RegisterTypespecParams()
				.withSpec("module SomeMod {typedef string foo;};")); //should work
		
		try {
			CLIENT1.registerTypespec(new RegisterTypespecParams()
					.withSpec("module SomeMod2 {typedef string foo;};"));
			fail("compiled spec without valid module");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					JUnitMatchers.containsString("Module SomeMod2 was not initialized"));
		}
	}

	@Test
	public void adminUserFacade() throws Exception {
		failAdmin(CLIENT2,
				"{\"command\": \"createWorkspace\"," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": [{\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"n\"," +
				"			   \"description\": \"mydesc\"}]}",
				"Unable to deserialize CreateWorkspaceParams out of params field.");
		
		failAdmin(CLIENT2,
				"{\"command\": [\"createWorkspace\"]," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"n\"," +
				"			   \"description\": \"mydesc\"}}",
				"Unable to deserialize a workspace admin command from the input.");
		
		TypeReference<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> typeref
				= new TypeReference<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>() {};
		TypeReference<List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>> listtyperef
				= new TypeReference<List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>>() {};
				
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT2.administer(new UObject(createData(
				"{\"command\": \"createWorkspace\"," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"n\"," +
				"			   \"description\": \"mydesc\"}}")))
				.asClassInstance(typeref);
		
		List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> mt =
				new ArrayList<Tuple9<Long,String,String,String,Long,String,String,String,Map<String,String>>>();
		List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> notexpected =
				new ArrayList<Tuple9<Long,String,String,String,Long,String,String,String,Map<String,String>>>();
		notexpected.add(CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace(USER1 + ":admintest")));
		checkWSInfoList(CLIENT2.listWorkspaceInfo(new ListWorkspaceInfoParams()), mt, notexpected);
		
		List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> got = 
				CLIENT2.administer(new UObject(createData(
						"{\"command\": \"listWorkspaces\"," +
						" \"user\": \"" + USER1 + "\"," +
						" \"params\": {}}"))).asClassInstance(listtyperef);
		checkWSInfoList(got, notexpected, mt);
		
		checkWS(wsinfo, wsinfo.getE1(), wsinfo.getE4(), USER1 + ":admintest", USER1, 0, "a", "n", "unlocked", "mydesc", MT_META);
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace(USER1 + ":admintest")
				.withNewPermission("r"));
		
		checkWS(CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withId(wsinfo.getE1())),
				wsinfo.getE1(), wsinfo.getE4(), USER1 + ":admintest", USER1, 0, "a", "r", "unlocked", "mydesc", MT_META);
		try {
			CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withId(wsinfo.getE1()));
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("User " + USER2 + " cannot read workspace " + wsinfo.getE1()));
		}
		
		failAdmin(CLIENT2, 
				"{\"command\": \"createWorkspace\"," +
				" \"user\": null," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"r\"," +
				"			   \"description\": \"mydesc\"}}",
				"User may not be null");
		failAdmin(CLIENT2, 
				"{\"command\": \"createWorkspace\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"r\"," +
				"			   \"description\": \"mydesc\"}}",
				"User may not be null");
		
		failAdmin(CLIENT2, 
				"{\"command\": \"createWorkspace\"," +
				" \"user\": \"thisisnotarealuserihopeorthistestwillfail\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"r\"," +
				"			   \"description\": \"mydesc\"}}",
				"thisisnotarealuserihopeorthistestwillfail is not a valid KBase user");
		failAdmin(CLIENT2, 
				"{\"command\": \"createWorkspace\"," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": null}", "Method parameters CreateWorkspaceParams may not be null");
		
		@SuppressWarnings("unchecked")
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> objinfo =
				list2ObjTuple11(((List<List<Object>>) CLIENT2.administer(new UObject(createData(
				"{\"command\": \"saveObjects\"," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"objects\": [{\"type\": \""  +
						SAFE_TYPE + "\", \"data\": {\"foo\": 1}, \"meta\": {\"b\": 2}}]}}")))
						.asInstance()).get(0));
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("foo", 1);
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("b", "2");
		checkInfo(objinfo, 1, "auto1", SAFE_TYPE, 1, USER1, wsinfo.getE1(),
				 USER1 + ":admintest", "51014459947d55c836fe74faf224e54a", 9,
				 meta);
		checkSavedObjects(Arrays.asList(new ObjectIdentity().withRef( USER1 + ":admintest/auto1")),
				1, "auto1", SAFE_TYPE, 1, USER1, wsinfo.getE1(),
				 USER1 + ":admintest", "51014459947d55c836fe74faf224e54a", 9,
				 meta, data);
		
		failAdmin(CLIENT2, 
				"{\"command\": \"saveObjects\"," +
				" \"user\": null," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"objects\": [{\"type\": \""  +
						SAFE_TYPE + "\", \"data\": {\"foo\": 1}, \"meta\": {\"b\": 2}}]}}",
				 "User may not be null");
		failAdmin(CLIENT2, 
				"{\"command\": \"saveObjects\"," +
				" \"user\": \"thisisalsonotavalidkbaseuserihope\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"objects\": [{\"type\": \""  +
						SAFE_TYPE + "\", \"data\": {\"foo\": 1}, \"meta\": {\"b\": 2}}]}}",
				"thisisalsonotavalidkbaseuserihope is not a valid KBase user");
		failAdmin(CLIENT2, 
				"{\"command\": \"saveObjects\"," +
						" \"user\": \"" + USER1 + "\"," +
				" \"params\": null}",
				"Method parameters SaveObjectsParams may not be null");
		
		WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");
		
		Map<String, Object> adminParams = new HashMap<String, Object>();
		adminParams.put("command", "getPermissions");
		adminParams.put("user", USER1);
		adminParams.put("params", ws);
		@SuppressWarnings("unchecked")
		Map<String, String> res = CLIENT2.administer(new UObject(adminParams)).asClassInstance(Map.class);
		assertThat("admin gets correct params", res, is(CLIENT1.getPermissions(ws)));
		
		adminParams.put("user", USER2);
		@SuppressWarnings("unchecked")
		Map<String, String> res2 = CLIENT2.administer(new UObject(adminParams)).asClassInstance(Map.class);
		assertThat("admin gets correct params", res2, is(CLIENT2.getPermissions(ws)));
		
		adminParams.put("user", "thisisacrazykbaseuserthatdoesntexistforsure");
		failAdmin(CLIENT2, adminParams, "thisisacrazykbaseuserthatdoesntexistforsure is not a valid KBase user");
		failAdmin(CLIENT1, adminParams, "User " + USER1 + " is not an admin");
		
		String wsstr = USER1 + ":admintest";
		
		adminParams.put("command", "setGlobalPermission");
		adminParams.put("user", USER1);
		adminParams.put("params", new SetGlobalPermissionsParams()
				.withWorkspace(wsstr).withNewPermission("n"));
		CLIENT2.administer(new UObject(adminParams));
		
		Map<String, String> expected = new HashMap<String, String>();
		expected.put(USER1, "a");
		assertThat("admin set global perm correctly", CLIENT1.getPermissions(ws),
				is(expected));
		
		adminParams.put("params", new SetGlobalPermissionsParams()
				.withWorkspace(wsstr).withNewPermission("r"));
		CLIENT2.administer(new UObject(adminParams));
		expected.put("*", "r");
		assertThat("admin set global perm correctly", CLIENT1.getPermissions(ws),
				is(expected));
		
		adminParams.put("user", USER2);
		failAdmin(CLIENT2, adminParams, "User " + USER2 + " may not set global permission on workspace " + wsstr);
		
		adminParams.put("command", "setPermissions");
		adminParams.put("params", new SetPermissionsParams().withWorkspace(wsstr)
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.administer(new UObject(adminParams));
		expected.put(USER2, "w");
		assertThat("admin set perm correctly", CLIENT1.getPermissions(ws),
				is(expected));
	}
	
	@Test
	public void getAllWorkspaceOwners() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("getAllWorkspaceOwners1"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("getAllWorkspaceOwners2"));
		String cmd = "{\"command\":\"listWorkspaceOwners\"}";
		List<String> owners = CLIENT2.administer(new UObject(
				new JsonTokenStream(cmd))).asInstance();
		Set<String> expected = new HashSet<String>(Arrays.asList(USER1, USER2));
		assertThat("returned expected users", new HashSet<String>(owners),
				is(expected));
		
		failAdmin(CLIENT1, cmd, "User " + USER1 + " is not an admin");
	}

	@Test
	public void checkFloat() throws Exception {
		final String specFloat =
				"module FloatSpec {" +
					"typedef structure {" +
						"float f;" +
					"} F;" +
				"};";
		CLIENT1.requestModuleOwnership("FloatSpec");
		administerCommand(CLIENT2, "approveModRequest", "module", "FloatSpec");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(specFloat)
			.withNewTypes(Arrays.asList("F")));
		String type = "FloatSpec.F-0.1";
		
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("float"));
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("f", 1.3e10);
		
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("float")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data))
				.withType(type).withName("f"))));
		
		Map<String, Object> got = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity()
				.withWorkspace("float").withName("f"))).get(0).getData().asInstance();
		assertThat("got correct float back", got, is(data));
	}
	
	@Test
	public void alterWorkspaceMetadata() throws Exception {
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("metadata");
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(wsi.getWorkspace()).withMeta(meta));
		checkWS(info, info.getE1(), info.getE4(), wsi.getWorkspace(), USER1, 0, "a",
				"n", "unlocked", null, meta);
		
		Map<String, String> newmeta = new HashMap<String, String>();
		newmeta.put("baz", "bing");
		newmeta.put("baf", "bat");
		meta.put("baz", "bing");
		meta.put("baf", "bat");
		meta.remove("foo");
		CLIENT1.alterWorkspaceMetadata(new AlterWorkspaceMetadataParams()
				.withRemove(Arrays.asList("foo")).withNew(newmeta)
				.withWsi(wsi));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info1 =
				CLIENT1.getWorkspaceInfo(wsi);
		checkWS(info1, info.getE1(), info1.getE4(), wsi.getWorkspace(), USER1, 0, "a",
				"n", "unlocked", null, meta);
		
		meta.remove("baz");
		CLIENT1.alterWorkspaceMetadata(new AlterWorkspaceMetadataParams()
				.withRemove(Arrays.asList("baz")).withWsi(wsi));
		info1 = CLIENT1.getWorkspaceInfo(wsi);
		checkWS(info1, info.getE1(), info1.getE4(), wsi.getWorkspace(), USER1, 0, "a",
		"n", "unlocked", null, meta);
		
		newmeta.clear();
		newmeta.put("baf", "thing");
		newmeta.put("123", "456");
		meta.put("baf", "thing");
		meta.put("123", "456");
		CLIENT1.alterWorkspaceMetadata(new AlterWorkspaceMetadataParams()
				.withNew(newmeta).withWsi(wsi));
		info1 = CLIENT1.getWorkspaceInfo(wsi);
		checkWS(info1, info.getE1(), info1.getE4(), wsi.getWorkspace(), USER1, 0, "a",
		"n", "unlocked", null, meta);
		
		failAlterWSMeta(CLIENT1, new AlterWorkspaceMetadataParams().withRemove(Arrays.asList("foo")),
				"WorkspaceIdentifier cannot be null");
		failAlterWSMeta(CLIENT1, new AlterWorkspaceMetadataParams().withWsi(wsi),
				"The new and remove params cannot both be null");
		failAlterWSMeta(CLIENT1, new AlterWorkspaceMetadataParams().withWsi(wsi)
				.withRemove(Arrays.asList("foo")).withNew(MT_META),
				"Metadata cannot be null or empty");
		failAlterWSMeta(CLIENT2, new AlterWorkspaceMetadataParams().withWsi(wsi)
				.withNew(newmeta),
				"User " + USER2 + " may not alter metadata for workspace " + wsi.getWorkspace());
		
		AlterWorkspaceMetadataParams p = new AlterWorkspaceMetadataParams();
		p.setAdditionalProperties("foo", "bar");
		failAlterWSMeta(CLIENT1, p, "Unexpected arguments in AlterWorkspaceMetadataParams: foo");
	}
	
	@Test
	public void testTypeMD5() throws Exception {
		String typeDefName = "SomeModule.AType";
		Map<String,String> type2md5 = CLIENT1.translateToMD5Types(Arrays.asList(typeDefName));
		String md5TypeDef = type2md5.get(typeDefName);
		Assert.assertNotNull(md5TypeDef);
		Map<String, List<String>> md52semantic = CLIENT1.translateFromMD5Types(Arrays.asList(md5TypeDef));
		Assert.assertEquals(1, md52semantic.size());
		Assert.assertTrue(md52semantic.get(md5TypeDef).contains("SomeModule.AType-1.0"));
	}
	
	@Test
	public void testGetInfo() throws Exception {
		WorkspaceClient cl = new WorkspaceClient(new URL("http://localhost:" + 
				SERVER2.getServerPort()));
		String module = "UnreleasedModule";
		try {
			cl.getModuleInfo(new GetModuleInfoParams().withMod(module));
			Assert.fail();
		} catch (Exception ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("Module wasn't uploaded: UnreleasedModule"));
		}
		Assert.assertEquals(1, cl.listModuleVersions(new ListModuleVersionsParams().withType("UnreleasedModule.AType-0.1")).getVers().size());
		Assert.assertTrue(cl.getJsonschema("UnreleasedModule.AType-0.1").length() > 0);
		cl = CLIENT_FOR_SRV2;
		Assert.assertTrue(new HashSet<String>(cl.listModules(new ListModulesParams().withOwner(USER2))).contains("UnreleasedModule"));
		Assert.assertEquals(0L, (long)cl.getModuleInfo(new GetModuleInfoParams().withMod(module)).getIsReleased());
		Assert.assertEquals(1, cl.listModuleVersions(new ListModuleVersionsParams().withMod(module)).getVers().size());
		Assert.assertEquals(1, cl.getTypeInfo("UnreleasedModule.AType").getTypeVers().size());
		Assert.assertEquals(1, cl.getTypeInfo("UnreleasedModule.AType-0.1").getTypeVers().size());
		Assert.assertTrue(cl.getJsonschema("UnreleasedModule.AType").length() > 0);
		Assert.assertEquals(1, cl.getFuncInfo("UnreleasedModule.aFunc").getFuncVers().size());
		try {
			cl.getTypeInfo("UnreleasedModule.AType-0.2");
			Assert.fail();
		} catch (Exception ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("Unable to locate type: UnreleasedModule.AType-0.2"));
		}
		try {
			cl.getJsonschema("UnreleasedModule.AType-0.2");
			Assert.fail();
		} catch (Exception ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("Unable to locate type: UnreleasedModule.AType-0.2"));
		}
	}
	
	@Test
	public void testSpecSync() throws Exception {
		CLIENT1.requestModuleOwnership("DepModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "DepModule");
		String urlForSrv2 = "http://localhost:" + SERVER2.getServerPort();
		ModuleVersions vers = CLIENT_FOR_SRV2.listModuleVersions(
				new ListModuleVersionsParams().withMod("DepModule"));
		long lastVer = CLIENT_FOR_SRV2.getModuleInfo(
				new GetModuleInfoParams().withMod("DepModule")).getVer();
		for (long ver : vers.getVers()) {
			boolean ok = true;
			try {
				CLIENT1.registerTypespecCopy(new RegisterTypespecCopyParams()
					.withExternalWorkspaceUrl(urlForSrv2).withMod("DepModule")
					.withVersion(ver));
			} catch (Exception ignore) {
				ok = false;
			}
			Assert.assertEquals(ver == lastVer, ok);
			if (ok) {
				CLIENT1.releaseModule("DepModule");
				Assert.assertTrue(CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod(
						"DepModule")).getTypes().containsKey("DepModule.BType-1.0"));
			}
		}
	}
	
	@Test
	public void testTypeAndModuleLookups() throws Exception {
		final String spec =
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> features;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid, string pattern) returns (Feature);" +
				"};";
		CLIENT1.requestModuleOwnership("TestModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "TestModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(spec)
			.withNewTypes(Arrays.asList("Feature","Genome")));
		CLIENT1.releaseModule("TestModule");
		
		// make sure the list of modules includes the TestModule
		Map<String,String> moduleNamesInList = new HashMap<String,String>();
		for(String mod: CLIENT1.listModules(new ListModulesParams())) {
			moduleNamesInList.put(mod, "");
		}
		Assert.assertTrue(moduleNamesInList.containsKey("TestModule"));
		
		// make sure that we can list the versions of this module, there should be just 2 visible to client1...
		Assert.assertEquals(
				2,
				CLIENT1.listModuleVersions(new ListModuleVersionsParams().withMod("TestModule")).getVers().size());
		
		// make sure we can retrieve module info
		Assert.assertEquals(
				2,
				CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod("TestModule")).getTypes().size());
		
		// make sure we can get a json schema and parse it as a json document
		ObjectMapper map = new ObjectMapper();
		JsonNode schema = map.readTree(CLIENT1.getJsonschema("TestModule.Feature"));
		Assert.assertEquals("Feature", schema.get("id").asText());
		
		// make sure we can get type info
		Assert.assertEquals("TestModule.Feature-1.0",CLIENT1.getTypeInfo("TestModule.Feature-1").getTypeDef());
		
		// make sure we can get func info
		Assert.assertEquals("TestModule.getFeature-1.0",CLIENT1.getFuncInfo("TestModule.getFeature").getFuncDef());
	}
	
	@Test
	public void testSpecRegError() throws Exception {
		WorkspaceClient cl = CLIENT2;
		cl.setIsInsecureHttpConnectionAllowed(true);
		cl.requestModuleOwnership("TestModule2");
		administerCommand(CLIENT2, "approveModRequest", "module", "TestModule2");
		cl.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module TestModule2{ typedef string StringType;};"));
		try {
			CLIENT1.registerTypespec(new RegisterTypespecParams()
				.withDryrun(0L)
				.withSpec("module TestModule2{ typedef int IntegerType;};"));
			Assert.fail();
		} catch (Exception ex) {
			Assert.assertEquals("User " + AUTH_USER1.getUserId() + " is not in list of owners of module TestModule2", ex.getMessage());
		}
//		administerCommand(CLIENT2, "grantModuleOwnership", "moduleName",
//				"TestModule2", "newOwner", AUTH_USER1.getUserId(), "withGrantOption", "1");
		
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"grantModuleOwnership\"," +
				" \"params\": {\"new_owner\": \"" + USER1 + "\", \"mod\": \"TestModule2\"," +
				"			   \"with_grant_option\": 1}}")));
		
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module TestModule2{ typedef int IntegerType;};"));
	}
	
	@Test
	public void testListAllTypes() throws Exception {
		Assert.assertTrue(CLIENT1.listAllTypes(new ListAllTypesParams().withWithEmptyModules(1L)).containsKey("RefSpec"));
		Map<String, Map<String, String>> types = CLIENT1.listAllTypes(new ListAllTypesParams());
		Assert.assertFalse(types.containsKey("RefSpec"));
		Assert.assertTrue(types.containsKey("SomeModule"));
		Assert.assertEquals("1.0", types.get("SomeModule").get("AType"));
	}
	
	@Test
	public void testGetAllTypeAndFuncInfo() throws Exception {
		Assert.assertEquals(1, CLIENT1.getAllTypeInfo("RefSpec").size());
		Assert.assertEquals(1, CLIENT_FOR_SRV2.getAllFuncInfo("UnreleasedModule").size());
	}
}
