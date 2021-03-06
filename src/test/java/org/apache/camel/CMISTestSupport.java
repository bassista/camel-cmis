package org.apache.camel;

import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CMISTestSupport extends CamelTestSupport {
    protected static final String CMIS_ENDPOINT_TEST_SERVER = "http://localhost:9090/chemistry-opencmis-server-inmemory/atom";
    protected static final String openCmisServerWarPath = "target/dependency/chemistry-opencmis-server-inmemory-war-0.5.0.war";

    protected static Server cmisServer;

    protected Exchange createExchangeWithInBody(String body) {
        DefaultExchange exchange = new DefaultExchange(context);
        if (body != null) {
            exchange.getIn().setBody(body);
        }
        return exchange;
    }

    protected CmisObject retrieveCMISObjectByIdFromServer(String nodeId) throws Exception {
        Session session = createSession();
        return session.getObject(nodeId);
    }

    protected void deleteAllContent() {
        Session session = createSession();
        Folder rootFolder = session.getRootFolder();
        ItemIterable<CmisObject> children = rootFolder.getChildren();
        for (CmisObject cmisObject : children) {
            if ("cmis:folder".equals(cmisObject.getPropertyValue(PropertyIds.OBJECT_TYPE_ID))) {
                List<String> notDeltedIdList = ((Folder) cmisObject).deleteTree(true, UnfileObject.DELETE, true);
                if (notDeltedIdList != null && notDeltedIdList.size() > 0) {
                    throw new RuntimeException("Cannot empty repo");
                }
            } else {
                cmisObject.delete(true);
            }
        }
        session.getBinding().close();
    }

    protected Session createSession() {
        SessionFactory sessionFactory = SessionFactoryImpl.newInstance();
        Map<String, String> parameter = new HashMap<String, String>();
        parameter.put(SessionParameter.ATOMPUB_URL, CMIS_ENDPOINT_TEST_SERVER);
        parameter.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());

        Repository repository = sessionFactory.getRepositories(parameter).get(0);
        return repository.createSession();
    }

    protected String getDocumentContentAsString(String nodeId) throws Exception {
        CmisObject cmisObject = retrieveCMISObjectByIdFromServer(nodeId);
        Document doc = (Document) cmisObject;
        InputStream inputStream = doc.getContentStream().getStream();
        return readFromStream(inputStream);
    }

    protected String readFromStream(InputStream in) throws Exception {
        StringBuilder result = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        String strLine;
        while ((strLine = br.readLine()) != null) {
            result.append(strLine);
        }
        in.close();
        return result.toString();
    }

    protected Folder createFolderWithName(String folderName) {
        Folder rootFolder = createSession().getRootFolder();
        return createChildFolderWithName(rootFolder, folderName);
    }

    protected Folder createChildFolderWithName(Folder parent, String childName) {
        Map<String, String> newFolderProps = new HashMap<String, String>();
        newFolderProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");
        newFolderProps.put(PropertyIds.NAME, childName);
        return parent.createFolder(newFolderProps);
    }

    protected void createTextDocument(Folder newFolder, String content, String fileName) throws UnsupportedEncodingException {
        byte[] buf = content.getBytes("UTF-8");
        ByteArrayInputStream input = new ByteArrayInputStream(buf);
        ContentStream contentStream = createSession().getObjectFactory().createContentStream(fileName, buf.length, "text/plain; charset=UTF-8", input);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
        properties.put(PropertyIds.NAME, fileName);
        newFolder.createDocument(properties, contentStream, VersioningState.NONE);
    }

    @BeforeClass
    public static void startServer() throws Exception {
        cmisServer = new Server(9090);
        WebAppContext openCmisServerApi = new WebAppContext(openCmisServerWarPath, "/chemistry-opencmis-server-inmemory");
        cmisServer.addHandler(openCmisServerApi);
        cmisServer.start();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        cmisServer.stop();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteAllContent();
        super.setUp();
    }

}
