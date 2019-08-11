package pt.uminho.ceb.biosystems.merlin.merlin_exporter;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uvigo.ei.aibench.core.Core;
import es.uvigo.ei.aibench.core.operation.OperationDefinition;
import es.uvigo.ei.aibench.core.operation.annotation.Direction;
import es.uvigo.ei.aibench.core.operation.annotation.Operation;
import es.uvigo.ei.aibench.core.operation.annotation.Port;
import es.uvigo.ei.aibench.core.operation.annotation.Progress;
import es.uvigo.ei.aibench.workbench.Workbench;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.LoadFromConf;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.MerlinUtils;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.TimeLeftProgress;
import pt.uminho.ceb.biosystems.merlin.dataAccess.InitDataAccess;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.DatabaseAccess;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.DatabaseSchemas;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.Enumerators.DatabaseType;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.H2DatabaseAccess;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;

/**
 * @author claudia
 *
 */

@Operation(name="import workspace", description="Import the workspace backup.")
public class RestoreDatabase implements PropertyChangeListener {
	private String zipPath;
	private String unzippedPath;
//	private String fileDate;
	private String workspaceName;
	private String destPath;
	private String importWS;
	private Boolean success = true;
	private String destWorkspaceName;
	private long startTime;
	private String message;
	private int dataSize;
	public TimeLeftProgress progress = new TimeLeftProgress();
	private AtomicBoolean cancel = new AtomicBoolean(false);

	final static Logger logger = LoggerFactory.getLogger(RestoreDatabase.class);

	@Port(name="new workspace name",description="set a name for the workspace", advanced = true, validateMethod="checkName", direction=Direction.INPUT,order=1)
	public void setProject(String name){

		destWorkspaceName = name;
	}


	/**
	 * @param directory
	 * @throws IOException 
	 */
	@Port(direction=Direction.INPUT, name="folder",description="workspace folder",validateMethod="checkDirectory",order=3)
	public void selectDirectory(File directory) throws IOException{
		try {
			//			unzippedPath = FileUtils.getHomeFolderPath().concat("ws").concat("/").concat(destWorkspaceName).concat("/");
			unzippedPath = FileUtils.getHomeFolderPath().concat("temp").concat("/").concat("importWorkspaceTemp").concat("/");

			logger.info("starting the .mer folder unzip..." );
			FileUtils.extractZipFile(zipPath, unzippedPath);
			
			workspaceName = importWorkspaceFolder();

			InitDataAccess.getInstance().getDatabaseExporterBatchService(this.destWorkspaceName).addPropertyChangeListener(this);
			this.message = "loading data";
			InitDataAccess.getInstance().getDatabaseExporterBatchService(this.destWorkspaceName).readxmldb(this.destPath.concat("/tables/"), this.cancel);
			
			InitDataAccess.getInstance().dropExporterConnection(this.destWorkspaceName);
			
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			

		} catch (Exception e) {
			success = false;

			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);

			e.printStackTrace();
			throw new IllegalArgumentException("Error while importing.");
		}

		if(success) {
			Workbench.getInstance().info("workspace "  + destWorkspaceName + " successfully imported");
			logger.info("workspace " + destWorkspaceName+ " successfully imported.");
			
			for (@SuppressWarnings("rawtypes") OperationDefinition def : Core.getInstance().getOperations()){
				if (def.getID().equals("operations.NewWorkspace.ID")){
					
					Workbench.getInstance().executeOperation(def);
				}
			}
		}

	}

	/**
	 * @param directory
	 */

	public void checkDirectory(File directory) {

		if(directory == null || directory.toString().isEmpty()) {
			success = false;
			throw new IllegalArgumentException("please select a .mer file!");
		}
		else {
			try {
				zipPath = directory.getPath();
				
				String extension;

				extension = FileUtils.getFileExtension(directory);
				if(extension.equals(null) || !extension.equals("mer")){
					success=false;
					throw new IllegalArgumentException("please select a .mer file!");
				}

			} catch (FileNotFoundException e) {
				success=false;
				e.printStackTrace();
				throw new IllegalArgumentException("please select a .mer file!");
			}

		}
	}
	
	private String importWorkspaceFolder() throws IOException {

		List <String> foldercontent = FileUtils.getFilesFromFolder(unzippedPath, false);
		String folder = foldercontent.get(0);
		importWS = unzippedPath.concat(folder).concat("/");
		List <String> finalFolder = FileUtils.getFilesFromFolder(importWS, false);
		workspaceName = "";
		for (String file : finalFolder) {
			String extension = FileUtils.getFileExtension(file);
			if(extension.equals("")) { //If the extension is empty, it means that it is a folder. Since there is only one folder, it is the workspace folder.

				workspaceName = file;
				break;
			}	
		}

		if(workspaceName.equals("")) {
			success = false;
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			Workbench.getInstance().error("workspace folder not found");
		}

		try {
			if(destWorkspaceName==null || destWorkspaceName.isEmpty())
				destWorkspaceName = workspaceName;
			
			logger.info("Starting the ws folder files import...");
			String cpy = importWS.concat(workspaceName).concat("/");
			File copy = new File(cpy);
			String pst = FileUtils.getWorkspacesFolderPath().concat(destWorkspaceName).concat("/");
			File paste = new File(pst);

			this.destPath = paste.getAbsolutePath();
			
			org.apache.commons.io.FileUtils.copyDirectory(copy, paste);

		} catch (Exception e) {
			success = false;
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
		}

		return workspaceName;
	}


	public void checkName(String name) {

		if(name!=null && !name.isEmpty())
			this.destWorkspaceName = name;
	}

	private Boolean validateDefaultCredentials() {
		String username = null, password = null, host = null, port = null;

		Map<String, String> credentials = LoadFromConf.loadDatabaseCredentials(FileUtils.getConfFolderPath());

		username = credentials.get("username");
		password = credentials.get("password");
		host = credentials.get("host");
		port = credentials.get("port");

		Boolean validCredentials = !(username.equals("your_username") && password.equals("your_password") && host.equals("your_ip_address") && port.equals("3306"));

		return validCredentials;

	}

	/**
	 * @return
	 */
	@Progress
	public TimeLeftProgress getProgress() {

		return progress;
	}
	
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		
		if(evt.getPropertyName().equalsIgnoreCase("message"))
			this.message = (String) evt.getNewValue();
		
		if(evt.getPropertyName().equalsIgnoreCase("size")) {
			this.dataSize = (int) evt.getNewValue();
		}
		
		if(evt.getPropertyName().equalsIgnoreCase("tablesCounter")) {
						
			int tablesCounter = (int) evt.getNewValue();
			this.progress.setTime((GregorianCalendar.getInstance().getTimeInMillis() - startTime), tablesCounter, dataSize, this.message);
		}
	}
}
