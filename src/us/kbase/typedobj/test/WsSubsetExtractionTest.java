package us.kbase.typedobj.test;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import us.kbase.typedobj.core.ExtractedSubsetAndMetadata;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;


/**
 * Tests that ensure the proper subset is extracted from a typed object instance
 *
 * To add new tests of the ID processing machinery, add files named in the format:
 *   [ModuleName].[TypeName].instance.[label] 
 *        - json encoding of a valid type instance
 *   [ModuleName].[TypeName].instance.[label].subset
 *        - json encoding of the expected '\@searchable ws_subset' output
 *
 * @author msneddon
 */
@RunWith(value = Parameterized.class)
public class WsSubsetExtractionTest {

	/**
	 * location to stash the temporary database for testing
	 * WARNING: THIS DIRECTORY WILL BE WIPED OUT AFTER TESTS!!!!
	 */
	private final static String TEST_DB_LOCATION = "test/typedobj_temp_test_files/t3";
	
	/**
	 * relative location to find the input files
	 */
	private final static String TEST_RESOURCE_LOCATION = "files/t3/";
	
	private final static boolean VERBOSE = true;

	private static TypeDefinitionDB db;
	private static TypedObjectValidator validator;
	
	/**
	 * List to stash the handle to the test case files
	 */
	private static List<TestInstanceInfo> instanceResources = new ArrayList <TestInstanceInfo> ();
	
	private static class TestInstanceInfo {
		public TestInstanceInfo(String resourceName, String moduleName, String typeName) {
			this.resourceName = resourceName;
			this.moduleName = moduleName;
			this.typeName = typeName;
		}
		public String resourceName;
		public String moduleName;
		public String typeName;
	}
	
	/**
	 * As each test instance object is created, this sets which instance to actually test
	 */
	private TestInstanceInfo instance;
	
	public WsSubsetExtractionTest(TestInstanceInfo tii) {
		this.instance = tii;
	}

	/**
	 * This is invoked before anything else, so here we invoke the creation of the db
	 * @return
	 * @throws Exception 
	 */
	@Parameters
	public static Collection<Object[]> assembleTestInstanceList() throws Exception {
		prepareDb();
		Object [][] instanceInfo = new Object[instanceResources.size()][1];
		for(int k=0; k<instanceResources.size(); k++) {
			instanceInfo[k][0] = instanceResources.get(k);
		}
		
		return Arrays.asList(instanceInfo);
	}
	
	
	/**
	 * Setup the typedef database, load and release the types in the simple specs, and
	 * identify all the files containing instances to validate.
	 * @throws Exception
	 */
	public static void prepareDb() throws Exception
	{
		
		//ensure test location is available
		File dir = new File(TEST_DB_LOCATION);
		if (dir.exists()) {
			//fail("database at location: "+TEST_DB_LOCATION+" already exists, remove/rename this directory first");
			removeDb();
		}
		if(!dir.mkdirs()) {
			fail("unable to create needed test directory: "+TEST_DB_LOCATION);
		}
		
		if(VERBOSE) System.out.println("setting up the typed obj database");
		// point the type definition db to point there
		File tempdir = new File("temp_files");
		if (!dir.exists())
			dir.mkdir();
		db = new TypeDefinitionDB(new FileTypeStorage(TEST_DB_LOCATION), tempdir, 
				new Util().getKIDLpath(), WorkspaceTestCommon.getKidlSource());
		
		
		// create a validator that uses the type def db
		validator = new TypedObjectValidator(db);
	
		String username = "wstester1";
		
		String kbSpec = loadResourceFile(TEST_RESOURCE_LOCATION+"KB.spec");
		List<String> kb_types =  Arrays.asList("SimpleStructure","MappingStruct","ListStruct","DeepMaps","NestedData", "MetaDataT1", "MetaDataT2", "MetaDataT3", "MetaDataT4");
		db.requestModuleRegistration("KB", username);
		db.approveModuleRegistrationRequest(username, "KB", true);
		db.registerModule(kbSpec ,kb_types, username);
		db.releaseModule("KB", username, false);
		
		if(VERBOSE) System.out.print("finding test instances...");
		String [] resources = getResourceListing(TEST_RESOURCE_LOCATION);
		for(int k=0; k<resources.length; k++) {
			String [] tokens = resources[k].split("\\.");
			if(tokens.length!=4) { continue; }
			if(tokens[2].equals("instance")) {
				instanceResources.add(new TestInstanceInfo(resources[k],tokens[0],tokens[1]));
			}
		}
		if(VERBOSE) System.out.println(" " + instanceResources.size() + " found");
	}
	
	
	@AfterClass
	public static void removeDb() throws IOException {
		File dir = new File(TEST_DB_LOCATION);
		FileUtils.deleteDirectory(dir);
		if(VERBOSE) System.out.println("deleting typed obj database");
	}
	
	@Test
	public void testValidInstances() throws Exception
	{
		ObjectMapper mapper = new ObjectMapper();
		
		//read the instance data
		if(VERBOSE) System.out.println("  -("+instance.resourceName+")");
		String instanceJson = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName);
		JsonNode instanceRootNode = mapper.readTree(instanceJson);
		
		// perform the initial validation, which must validate!
		TypedObjectValidationReport report = 
			validator.validate(
				instanceRootNode,
				new TypeDefId(new TypeDefName(instance.moduleName,instance.typeName))
				);
		List <String> mssgs = report.getErrorMessages();
		for(int i=0; i<mssgs.size(); i++) {
			System.out.println("    ["+i+"]:"+mssgs.get(i));
		}
		assertTrue("  -("+instance.resourceName+") does not validate, but should",
				report.isInstanceValid());
		
		ExtractedSubsetAndMetadata extraction = report.extractSearchableWsSubsetAndMetadata(-1);
		JsonNode actualSubset = extraction.getWsSearchableSubset();
		JsonNode actualMetadata = extraction.getMetadata();
		
		try {
			String expectedSubsetString = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName+".subset");
			JsonNode expectedSubset = mapper.readTree(expectedSubsetString);
			//System.out.println("got subdata: "+sortJson(actualSubset));
			//System.out.println("exp subdata: "+sortJson(expectedSubset));
			compare(expectedSubset, actualSubset, instance.resourceName+" -- subset");
		} catch (IllegalStateException e) {}
		try {
			String expectedMetadataString = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName+".metadata");
			JsonNode expectedMetadata = mapper.readTree(expectedMetadataString);
			//System.out.println("got metadata: "+sortJson(actualMetadata));
			//System.out.println("exp metadata: "+sortJson(expectedMetadata));
			compare(expectedMetadata, actualMetadata, instance.resourceName+" -- metadata");
		} catch (IllegalStateException e) {}
		System.out.println("       PASS");
	}

	public void compare(JsonNode expectedSubset, JsonNode actualSubset, String resourceName) throws IOException {
		assertEquals("  -("+resourceName+") extracted subset/metadata does not match expected extracted subset/metadata",
				sortJson(expectedSubset), sortJson(actualSubset));
	}

	private static JsonNode sortJson(JsonNode tree) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		Object map = mapper.treeToValue(tree, Object.class);
		String text = mapper.writeValueAsString(map);
		return mapper.readTree(text);
	}

	/**
	 * helper method to load test files, mostly copied from TypeRegistering test
	 */
	private static String loadResourceFile(String resourceName) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = WsSubsetExtractionTest.class.getResourceAsStream(resourceName);
		if (is == null)
			throw new IllegalStateException("Resource not found: " + resourceName);
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		while (true) {
			String line = br.readLine();
			if (line == null)
				break;
			pw.println(line);
		}
		br.close();
		pw.close();
		return sw.toString();
	}
	
	
	
	/**
	 * List directory contents for a resource folder. Not recursive.
	 * This is basically a brute-force implementation.
	 * Works for regular files and also JARs.
	 * adapted from: http://www.uofr.net/~greg/java/get-resource-listing.html
	 * 
	 * @author Greg Briggs
	 * @author msneddon
	 * @param path Should end with "/", but not start with one.
	 * @return Just the name of each member item, not the full paths.
	 * @throws URISyntaxException 
	 * @throws IOException 
	 */
	private static String[] getResourceListing(String path) throws URISyntaxException, IOException {
		URL dirURL = WsSubsetExtractionTest.class.getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			/* A file path: easy enough */
			return new File(dirURL.toURI()).list();
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as the class.
			String me = WsSubsetExtractionTest.class.getName().replace(".", "/")+".class";
			dirURL = WsSubsetExtractionTest.class.getResource(me);
		}

		if (dirURL.getProtocol().equals("jar")) {
			/* A JAR path */
			String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")); //strip out only the JAR file
			JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
			Enumeration<JarEntry> entries = jar.entries(); //gives ALL entries in jar
			Set<String> result = new HashSet<String>(); //avoid duplicates in case it is a subdirectory
			while(entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				// construct internal jar path relative to the class
				String fullPath = WsSubsetExtractionTest.class.getPackage().getName().replace(".","/") + "/" + path;
				if (name.startsWith(fullPath)) { //filter according to the path
					String entry = name.substring(fullPath.length());
					int checkSubdir = entry.indexOf("/");
					if (checkSubdir >= 0) {
						// if it is a subdirectory, we just return the directory name
						entry = entry.substring(0, checkSubdir);
					}
					result.add(entry);
				}
			}
			jar.close();
			return result.toArray(new String[result.size()]);
		}
		throw new UnsupportedOperationException("Cannot list files for URL "+dirURL);
	}
	
}
