package us.kbase.workspace;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientCaller;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.Tuple10;
import us.kbase.common.service.Tuple6;
import us.kbase.common.service.Tuple9;

/**
 * <p>Original spec-file module name: Workspace</p>
 * <pre>
 * The workspace service at its core is a storage and retrieval system for 
 * typed objects. Objects are organized by the user into one or more workspaces.
 * Features:
 * Versioning of objects
 * Data provenenance
 * Object to object references
 * Workspace sharing
 * ***Add stuff here***
 * Notes about deletion and GC
 * BINARY DATA:
 * All binary data must be hex encoded prior to storage in a workspace. 
 * Attempting to send binary data via a workspace client will cause errors.
 * </pre>
 */
public class WorkspaceClient {
    private JsonClientCaller caller;
    private static URL DEFAULT_URL = null;
    static {
        try {
            DEFAULT_URL = new URL("http://kbase.us/services/workspace/");
        } catch (MalformedURLException mue) {
            throw new RuntimeException("Compile error in client - bad url compiled");
        }
    }

    public WorkspaceClient() {
       caller = new JsonClientCaller(DEFAULT_URL);
    }

    public WorkspaceClient(URL url) {
        caller = new JsonClientCaller(url);
    }

    public WorkspaceClient(URL url, AuthToken token) {
        caller = new JsonClientCaller(url, token);
    }

    public WorkspaceClient(URL url, String user, String password) {
        caller = new JsonClientCaller(url, user, password);
    }

    public WorkspaceClient(AuthToken token) {
        caller = new JsonClientCaller(DEFAULT_URL, token);
    }

    public WorkspaceClient(String user, String password) {
        caller = new JsonClientCaller(DEFAULT_URL, user, password);
    }

    public boolean isAuthAllowedForHttp() {
        return caller.isAuthAllowedForHttp();
    }

    public void setAuthAllowedForHttp(boolean isAuthAllowedForHttp) {
        caller.setAuthAllowedForHttp(isAuthAllowedForHttp);
    }

    /**
     * <p>Original spec-file function name: create_workspace</p>
     * <pre>
     * Creates a new workspace.
     * </pre>
     * @param   params   Original type "CreateWorkspaceParams" (see {@link us.kbase.workspace.CreateWorkspaceParams CreateWorkspaceParams} for details)
     * @return   Original type "workspace_metadata" (Meta data associated with a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple6<Integer, String, String, String, String, String> createWorkspace(CreateWorkspaceParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple6<Integer, String, String, String, String, String>>> retType = new TypeReference<List<Tuple6<Integer, String, String, String, String, String>>>() {};
        List<Tuple6<Integer, String, String, String, String, String>> res = caller.jsonrpcCall("Workspace.create_workspace", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_workspace_metadata</p>
     * <pre>
     * Get a workspace's metadata.
     * </pre>
     * @param   wsi   Original type "WorkspaceIdentity" (see {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity} for details)
     * @return   Original type "workspace_metadata" (Meta data associated with a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple6<Integer, String, String, String, String, String> getWorkspaceMetadata(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<List<Tuple6<Integer, String, String, String, String, String>>> retType = new TypeReference<List<Tuple6<Integer, String, String, String, String, String>>>() {};
        List<Tuple6<Integer, String, String, String, String, String>> res = caller.jsonrpcCall("Workspace.get_workspace_metadata", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_workspace_description</p>
     * <pre>
     * Get a workspace's description.
     * </pre>
     * @param   wsi   Original type "WorkspaceIdentity" (see {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity} for details)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public String getWorkspaceDescription(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<List<String>> retType = new TypeReference<List<String>>() {};
        List<String> res = caller.jsonrpcCall("Workspace.get_workspace_description", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: set_permissions</p>
     * <pre>
     * Set permissions for a workspace.
     * </pre>
     * @param   params   Original type "SetPermissionsParams" (see {@link us.kbase.workspace.SetPermissionsParams SetPermissionsParams} for details)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void setPermissions(SetPermissionsParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.set_permissions", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: get_permissions</p>
     * <pre>
     * Get permissions for a workspace.
     * </pre>
     * @param   wsi   Original type "WorkspaceIdentity" (see {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity} for details)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Map<String,String> getPermissions(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<List<Map<String,String>>> retType = new TypeReference<List<Map<String,String>>>() {};
        List<Map<String,String>> res = caller.jsonrpcCall("Workspace.get_permissions", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: save_objects</p>
     * <pre>
     * Save objects to the workspace. Saving over a deleted object undeletes
     * it.
     * </pre>
     * @param   params   Original type "SaveObjectsParams" (see {@link us.kbase.workspace.SaveObjectsParams SaveObjectsParams} for details)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple9<Integer, String, String, String, Integer, String, Integer, String, Integer>> saveObjects(SaveObjectsParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<List<Tuple9<Integer, String, String, String, Integer, String, Integer, String, Integer>>>> retType = new TypeReference<List<List<Tuple9<Integer, String, String, String, Integer, String, Integer, String, Integer>>>>() {};
        List<List<Tuple9<Integer, String, String, String, Integer, String, Integer, String, Integer>>> res = caller.jsonrpcCall("Workspace.save_objects", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_objects</p>
     * <pre>
     * Get objects from the workspace.
     * </pre>
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<ObjectData> getObjects(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<List<List<ObjectData>>> retType = new TypeReference<List<List<ObjectData>>>() {};
        List<List<ObjectData>> res = caller.jsonrpcCall("Workspace.get_objects", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_object_metadata</p>
     * <pre>
     * Get object metadata from the workspace.
     * </pre>
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,String>>> getObjectMetadata(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<List<List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,String>>>>> retType = new TypeReference<List<List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,String>>>>>() {};
        List<List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,String>>>> res = caller.jsonrpcCall("Workspace.get_object_metadata", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: delete_objects</p>
     * <pre>
     * Delete objects. All versions of an object are deleted, regardless of
     * the version specified in the ObjectIdentity. If an object is already
     * deleted, no error is thrown.
     * </pre>
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void deleteObjects(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.delete_objects", args, retType, false, false);
    }

    /**
     * <p>Original spec-file function name: undelete_objects</p>
     * <pre>
     * Undelete objects. All versions of an object are undeleted, regardless
     * of the version specified in the ObjectIdentity. If an object is not
     * deleted, no error is thrown.
     * </pre>
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void undeleteObjects(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.undelete_objects", args, retType, false, false);
    }

    /**
     * <p>Original spec-file function name: delete_workspace</p>
     * <pre>
     * Delete a workspace. All objects contained in the workspace are deleted.
     * </pre>
     * @param   wsi   Original type "WorkspaceIdentity" (see {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity} for details)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void deleteWorkspace(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.delete_workspace", args, retType, false, false);
    }

    /**
     * <p>Original spec-file function name: undelete_workspace</p>
     * <pre>
     * Undelete a workspace. All objects contained in the workspace are
     * undeleted, regardless of their state at the time the workspace was
     * deleted.
     * </pre>
     * @param   wsi   Original type "WorkspaceIdentity" (see {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity} for details)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void undeleteWorkspace(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.undelete_workspace", args, retType, false, false);
    }
}
