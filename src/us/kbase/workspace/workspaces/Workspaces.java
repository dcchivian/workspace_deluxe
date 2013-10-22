package us.kbase.workspace.workspaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.OwnerInfo;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.BadJsonSchemaDocumentException;
import us.kbase.typedobj.exceptions.InstanceValidationException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.SpecParseException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ReferenceParser;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.ObjectUserMetaData;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class Workspaces {
	
	//TODO rename workspace / object
	//TODO clone workspace
	//TODO get history of object
	//TODO copy object(s)
	//TODO move objects(s) ? needed?
	//TODO revert object
	//TODO lock workspace, publish workspace
	//TODO list workspaces w/ filters on globalread, user, deleted (ONWER)
	//TODO list objects w/ filters on ws, creator, type, meta, deleted (WRITE), hidden
	//TODO get object changes since date (based on type collection and pointers collection
	//TODO set global read
	//TODO set description
	//TODO garbage collection - make a static thread that calls a gc() method, waits until all reads done - read counting, read methods must register to static object
	
	//TODO length limits on all incoming strings
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	
	private final WorkspaceDatabase db;
	private final TypeDefinitionDB typedb;
	private final ReferenceParser refparse;
	
	public Workspaces(final WorkspaceDatabase db,
			final ReferenceParser refparse) {
		if (db == null) {
			throw new IllegalArgumentException("db cannot be null");
		}
		if (refparse == null) {
			throw new IllegalArgumentException("refparse cannot be null");
		}
		this.db = db;
		typedb = db.getTypeValidator().getDB();
		this.refparse = refparse;
	}
	
	private void comparePermission(final WorkspaceUser user,
			final Permission required, final Permission available,
			final ObjectIdentifier oi, final String operation) throws
			WorkspaceAuthorizationException {
		final WorkspaceAuthorizationException wae =
				comparePermission(user, required, available,
						oi.getWorkspaceIdentifierString(), operation);
		if (wae != null) {
			wae.addDeniedCause(oi);
			throw wae;
		}
	}
	
	private void comparePermission(final WorkspaceUser user,
			final Permission required, final Permission available,
			final WorkspaceIdentifier wsi, final String operation) throws
			WorkspaceAuthorizationException {
		final WorkspaceAuthorizationException wae =
				comparePermission(user, required, available,
						wsi.getIdentifierString(), operation);
		if (wae != null) {
			wae.addDeniedCause(wsi);
			throw wae;
		}
	}
	
	private WorkspaceAuthorizationException comparePermission(
			final WorkspaceUser user, final Permission required,
			final Permission available, final String identifier,
			final String operation) {
		if(required.compareTo(available) > 0) {
			final String err = user == null ?
					"Anonymous users may not %s workspace %s" :
					"User " + user.getUser() + " may not %s workspace %s";
			final WorkspaceAuthorizationException wae = 
					new WorkspaceAuthorizationException(String.format(
					err, operation, identifier));
			return wae;
		}
		return null;
	}
	
	private ResolvedWorkspaceID checkPerms(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final Permission perm,
			final String operation)
			throws CorruptWorkspaceDBException, WorkspaceAuthorizationException,
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		return checkPerms(user, wsi, perm, operation, false);
	}
	
	private ResolvedWorkspaceID checkPerms(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final Permission perm,
			final String operation, boolean allowDeletedWorkspace)
			throws CorruptWorkspaceDBException, WorkspaceAuthorizationException,
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi,
				allowDeletedWorkspace);
		comparePermission(user, perm, db.getPermission(user, wsid),
				wsi, operation);
		return wsid;
	}
	
	private Map<ObjectIdentifier, ObjectIDResolvedWS> checkPerms(
			final WorkspaceUser user, final List<ObjectIdentifier> loi,
			final Permission perm, final String operation) throws
			WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException {
		if (loi.isEmpty()) {
			throw new IllegalArgumentException("No object identifiers provided");
		}
		//map is for error purposes only - only stores the most recent object
		//associated with a workspace
		final Map<WorkspaceIdentifier, ObjectIdentifier> wsis =
				new HashMap<WorkspaceIdentifier, ObjectIdentifier>();
		for (final ObjectIdentifier o: loi) {
			wsis.put(o.getWorkspaceIdentifier(), o);
		}
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis;
		try {
				rwsis = db.resolveWorkspaces(wsis.keySet());
		} catch (NoSuchWorkspaceException nswe) {
			final ObjectIdentifier obj = wsis.get(nswe.getMissingWorkspace());
			throw new InaccessibleObjectException(String.format(
					"Object %s cannot be accessed: %s",
					obj.getIdentifierString(), nswe.getLocalizedMessage()),
					obj, nswe);
		}
		final Map<ResolvedWorkspaceID, Permission> perms =
				db.getPermissions(user,
						new HashSet<ResolvedWorkspaceID>(rwsis.values()));
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ret =
				new HashMap<ObjectIdentifier, ObjectIDResolvedWS>();
		for (final ObjectIdentifier o: loi) {
			final ResolvedWorkspaceID r = rwsis.get(o.getWorkspaceIdentifier());
			try {
				comparePermission(user, perm, perms.get(r), o, operation);
			} catch (WorkspaceAuthorizationException wae) {
				throw new InaccessibleObjectException(String.format(
						"Object %s cannot be accessed: %s",
						o.getIdentifierString(), wae.getLocalizedMessage()),
						o, wae);
			}
			ret.put(o, o.resolveWorkspace(r));
		}
		return ret;
	}
	
	public WorkspaceMetaData createWorkspace(final WorkspaceUser user, 
			final String wsname, boolean globalread, String description)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		new WorkspaceIdentifier(wsname, user); //check for errors
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		return db.createWorkspace(user, wsname, globalread, description);
	}
	
	public String getWorkspaceDescription(final WorkspaceUser user,
			final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException,
			WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		return db.getWorkspaceDescription(wsid);
	}

	public void setPermissions(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final List<WorkspaceUser> users,
			final Permission permission) throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		if (Permission.OWNER.compareTo(permission) <= 0) {
			throw new IllegalArgumentException("Cannot set owner permission");
		}
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"set permissions on");
		db.setPermissions(wsid, users, permission);
	}

	public Map<User, Permission> getPermissions(final WorkspaceUser user,
				final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
				WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (user == null) {
			throw new IllegalArgumentException("User cannot be null");
		}
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi);
		final Map<User, Permission> perms =
				db.getUserAndGlobalPermission(user, wsid);
		if (Permission.ADMIN.compareTo(perms.get(user)) > 0) {
			return perms;
		}
		return db.getAllPermissions(wsid);
	}

	public WorkspaceMetaData getWorkspaceMetaData(final WorkspaceUser user,
				final WorkspaceIdentifier wsi) throws
				NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		return db.getWorkspaceMetadata(user, wsid);
	}
	
	public String getBackendType() {
		return db.getBackendType();
	}
	
	private static String getObjectErrorId(final ObjectIDNoWSNoVer oi,
			final int objcount) {
		String objErrId = "#" + objcount;
		objErrId += oi == null ? "" : ", " + oi.getIdentifierString();
		return objErrId;
	}
	
	public List<ObjectMetaData> saveObjects(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, 
			final List<WorkspaceSaveObject> objects) throws
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			NoSuchObjectException, CorruptWorkspaceDBException,
			NoSuchWorkspaceException, NoSuchTypeException,
			NoSuchModuleException, TypeStorageException,
			TypedObjectValidationException,
			BadJsonSchemaDocumentException, InstanceValidationException { //TODO get rid of these when possible
		if (objects.isEmpty()) {
			throw new IllegalArgumentException("No data provided");
		}
		final ResolvedWorkspaceID rwsi = checkPerms(user, wsi, Permission.WRITE,
				"write to");
		final TypedObjectValidator val = db.getTypeValidator();
		class TempObjectData {
			public WorkspaceSaveObject wo;
			public TypedObjectValidationReport rep;
			public int order;
			
			public TempObjectData(WorkspaceSaveObject wo,
					TypedObjectValidationReport rep, int order) {
				this.wo = wo;
				this.rep = rep;
				this.order = order;
			}
			
		}
		//this method must maintain the order of the objects
		//TODO tests for validation of objects
		final Map<String, ObjectIdentifier> refToOid =
				new HashMap<String, ObjectIdentifier>();
		//note this only contains the first object encountered with the ref.
		// For error reporting purposes only
		final Map<ObjectIdentifier, TempObjectData> oidToObject =
				new HashMap<ObjectIdentifier, TempObjectData>();
		final Map<WorkspaceSaveObject, TempObjectData> reports = 
				new HashMap<WorkspaceSaveObject, TempObjectData>();
		int objcount = 1;
		
		
		//stage 1: validate & extract & parse references
		for (WorkspaceSaveObject wo: objects) {
			final ObjectIDNoWSNoVer oid = wo.getObjectIdentifier();
			final String objerrid = getObjectErrorId(oid, objcount);
			final TypedObjectValidationReport rep =
					val.validate(wo.getData(), wo.getType());
			if (!rep.isInstanceValid()) {
				final String[] e = rep.getErrorMessages();
				final String err = StringUtils.join(e, "\n");
				throw new TypedObjectValidationException(String.format(
						"Object %s failed type checking:\n", objerrid) + err);
			}
			final TempObjectData data = new TempObjectData(wo, rep, objcount);
			for (final String ref: rep.getListOfIdReferences()) {
				try {
					if (!refToOid.containsKey(ref)) {
						final ObjectIdentifier oi = refparse.parse(ref);
						refToOid.put(ref, oi);
						oidToObject.put(oi, data);
					}
				} catch (IllegalArgumentException iae) {
					throw new TypedObjectValidationException(String.format(
							"Object %s has unparseable reference %s: %s",
							objerrid, ref, iae.getLocalizedMessage(), iae));
				}
			}
			reports.put(wo, data);
		}
		
		//stage 2: resolve references and get types
		final Map<ObjectIdentifier, ObjectIDResolvedWS> wsresolvedids;
		if (!oidToObject.isEmpty()) {
			try {
					wsresolvedids = checkPerms(user,
							new LinkedList<ObjectIdentifier>(oidToObject.keySet()),
							Permission.READ, "read");
			} catch (InaccessibleObjectException ioe) {
				final ObjectIdentifier cause = ioe.getInaccessibleObject();
				String ref = null; //must be set correctly below
				for (final String r: refToOid.keySet()) {
					if (refToOid.get(r).equals(cause)) {
						ref = r;
						break;
					}
				}
				final TempObjectData tod = oidToObject.get(cause);
				final String objerrid = getObjectErrorId(
						tod.wo.getObjectIdentifier(), tod.order);
				throw new TypedObjectValidationException(String.format(
						"Object %s has inaccessible reference %s: %s",
						objerrid, ref, ioe.getLocalizedMessage(), ioe));
			}
		} else {
			wsresolvedids = new HashMap<ObjectIdentifier, ObjectIDResolvedWS>();
		}
//		final Map<ObjectIDResolvedWS, Res>
//		if (!wsresolvedids.isEmpty()) {
//			try {
//				
//			}
//		}
		
		final List<ResolvedSaveObject> saveobjs =
				new ArrayList<ResolvedSaveObject>();
		for (WorkspaceSaveObject wo: objects) {

			final AbsoluteTypeDefId type = reports.get(wo).rep
					.getValidationTypeDefId();
			saveobjs.add(wo.resolve(type, wo.getData()));//TODO rewrite data
			objcount++;
		}
		//TODO check size < 1 MB
		//TODO resolve references (std resolve, resolve to IDs, no resolution)
		//TODO make sure all object and provenance references exist aren't deleted, convert to perm refs - batch
		//TODO rewrite references
		//TODO when safe, add references to references collection
		//TODO replace object in workspace object
		return db.saveObjects(user, rwsi, saveobjs);
	}
	
	public List<WorkspaceObjectData> getObjects(final WorkspaceUser user,
			final List<ObjectIdentifier> loi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException,
			InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, WorkspaceObjectData> data = 
				db.getObjects(new HashSet<ObjectIDResolvedWS>(ws.values()));
		final List<WorkspaceObjectData> ret =
				new ArrayList<WorkspaceObjectData>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(data.get(ws.get(o)));
		}
		return ret;
	}
	
	public List<ObjectUserMetaData> getObjectMetaData(final WorkspaceUser user,
			final List<ObjectIdentifier> loi) throws 
			WorkspaceCommunicationException, CorruptWorkspaceDBException,
			InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, ObjectUserMetaData> meta = 
				db.getObjectMeta(new HashSet<ObjectIDResolvedWS>(ws.values()));
		final List<ObjectUserMetaData> ret =
				new ArrayList<ObjectUserMetaData>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(meta.get(ws.get(o)));
		}
		return ret;
	}
	
	public void setObjectsDeleted(final WorkspaceUser user,
			final List<ObjectIdentifier> loi, final boolean delete)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
			InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.WRITE,
						(delete ? "" : "un") + "delete objects from");
		db.setObjectsDeleted(new HashSet<ObjectIDResolvedWS>(ws.values()),
				delete);
	}

	public void setWorkspaceDeleted(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final boolean delete)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.OWNER,
				(delete ? "" : "un") + "delete", !delete);
		db.setWorkspaceDeleted(wsid, delete);
	}

	//TODO tests for module registration/compile/list/update etc.
	public void requestModuleRegistration(final WorkspaceUser user,
			final String module) throws TypeStorageException {
		if (typedb.isValidModule(module)) {
			throw new IllegalArgumentException(module +
					" module already exists");
		}
		typedb.requestModuleRegistration(module, user.getUser());
	}
	
	public List<OwnerInfo> listModuleRegistrationRequests() throws
			TypeStorageException {
		try {
			return typedb.getNewModuleRegistrationRequests(null);
		} catch (NoSuchPrivilegeException nspe) {
			throw new RuntimeException(
					"Something is broken in the administration system", nspe);
		}
	}
	
	public void resolveModuleRegistration(final String module,
			final boolean approve)
			throws TypeStorageException {
		try {
			if (approve) {
				typedb.approveModuleRegistrationRequest(null, module);
			} else {
				typedb.refuseModuleRegistrationRequest(null, module);
			}
		} catch (NoSuchPrivilegeException nspe) {
			throw new RuntimeException(
					"Something is broken in the administration system", nspe);
		}
	}
	
	//TODO should return the version as well?
	public Map<TypeDefName, TypeChange> compileNewTypeSpec(
			final WorkspaceUser user, final String typespec,
			final List<String> newtypes, final List<String> removeTypes,
			final Map<String, Long> moduleVers, final boolean dryRun,
			final Long previousVer)
			throws SpecParseException, TypeStorageException,
			NoSuchPrivilegeException, NoSuchModuleException {
		return typedb.registerModule(typespec, newtypes, removeTypes,
				user.getUser(), dryRun, moduleVers, previousVer);
	}
	
	public Map<TypeDefName, TypeChange> compileTypeSpec(
			final WorkspaceUser user, final String module,
			final List<String> newtypes, final List<String> removeTypes,
			final Map<String, Long> moduleVers, boolean dryRun)
			throws SpecParseException, TypeStorageException,
			NoSuchPrivilegeException, NoSuchModuleException {
		return typedb.refreshModule(module, newtypes, removeTypes,
				user.getUser(), dryRun, moduleVers);
	}
	
	public List<AbsoluteTypeDefId> releaseTypes(final WorkspaceUser user,
			final String module)
			throws NoSuchModuleException, TypeStorageException,
			NoSuchPrivilegeException {
		return typedb.releaseModule(module, user.getUser());
	}
	
	public String getJsonSchema(final TypeDefId type) throws
			NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return typedb.getJsonSchemaDocument(type);
	}
	
	public List<String> listModules(WorkspaceUser user)
			throws TypeStorageException {
		if (user == null) {
			return typedb.getAllRegisteredModules();
		}
		return typedb.getModulesByOwner(user.getUser());
	}
	
	//TODO version collection. To refs stored with each version pointer for prov and normal refs. ref counts for from references. 
	
	public ModuleInfo getModuleInfo(final ModuleDefId module)
			throws NoSuchModuleException, TypeStorageException {
		final us.kbase.typedobj.db.ModuleInfo moduleInfo =
				typedb.getModuleInfo(module);
		return new ModuleInfo(typedb.getModuleSpecDocument(module),
				typedb.getModuleOwners(module.getModuleName()),
				moduleInfo.getVersionTime(),  moduleInfo.getDescription(),
				typedb.getJsonSchemasForAllTypes(module));
	}
	
	public List<Long> getModuleVersions(final String module)
			throws NoSuchModuleException, TypeStorageException {
		return typedb.getAllModuleVersions(module);
	}
	
	public List<Long> getModuleVersions(final TypeDefId type) 
			throws NoSuchModuleException, TypeStorageException,
			NoSuchTypeException {
		final List<ModuleDefId> mods =
				typedb.findModuleVersionsByTypeVersion(type);
		final List<Long> vers = new ArrayList<Long>();
		for (final ModuleDefId m: mods) {
			vers.add(m.getVersion());
		}
		return vers;
	}
}
