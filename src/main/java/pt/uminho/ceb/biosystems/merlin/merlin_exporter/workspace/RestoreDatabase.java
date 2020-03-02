package pt.uminho.ceb.biosystems.merlin.merlin_exporter.workspace;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uvigo.ei.aibench.core.Core;
import es.uvigo.ei.aibench.core.operation.OperationDefinition;
import es.uvigo.ei.aibench.core.operation.annotation.Cancel;
import es.uvigo.ei.aibench.core.operation.annotation.Direction;
import es.uvigo.ei.aibench.core.operation.annotation.Operation;
import es.uvigo.ei.aibench.core.operation.annotation.Port;
import es.uvigo.ei.aibench.core.operation.annotation.Progress;
import es.uvigo.ei.aibench.workbench.Workbench;
import pt.uminho.ceb.biosystems.merlin.gui.jpanels.CustomGUI;
import pt.uminho.ceb.biosystems.merlin.gui.utilities.TimeLeftProgress;
import pt.uminho.ceb.biosystems.merlin.services.DatabaseServices;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;

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
	private Boolean override = false;

	final static Logger logger = LoggerFactory.getLogger(RestoreDatabase.class);
	
	@Port(direction=Direction.INPUT, name="force database creation", validateMethod="checkIfOverride", description="this command forces merlin to create a database with the seleted name. If a database with such name already exists, it will be replaced",
			advanced = true, defaultValue = "false", order=1)
	public void setOldWorkspaceName(boolean override) {}

	@Port(name="new workspace name",description="set a name for the workspace", advanced = true, validateMethod="checkName", direction=Direction.INPUT,order=2)
	public void setProject(String name){
		
	}
	
	/**
	 * @param directory
	 * @throws IOException 
	 */
	@Port(direction=Direction.INPUT, name="folder",description="workspace folder",validateMethod="checkDirectory",order=3)
	public void selectDirectory(File directory) throws IOException{
		try {
			this.startTime = GregorianCalendar.getInstance().getTimeInMillis();
			this.cancel = new AtomicBoolean(false);
			
			//			unzippedPath = FileUtils.getHomeFolderPath().concat("ws").concat("/").concat(destWorkspaceName).concat("/");
			unzippedPath = FileUtils.getHomeFolderPath().concat("temp").concat("/").concat("importWorkspaceTemp").concat("/");

			logger.info("starting the .mer folder unzip..." );
			FileUtils.extractZipFile(zipPath, unzippedPath);
			
			workspaceName = importWorkspaceFolder();

			this.message = "loading data";
			
			DatabaseServices.generateDatabase(this.destWorkspaceName);
			DatabaseServices.dropConnection(this.destWorkspaceName);
			
			DatabaseServices.readxmldb(this.destWorkspaceName, this.destPath.concat("/tables/"), this.cancel, this);
			
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			
			File tablesWorkspace = new File(FileUtils.getWorkspacesFolderPath().concat(this.destWorkspaceName).concat("/tables"));
			if(tablesWorkspace.exists())
				org.apache.commons.io.FileUtils.deleteDirectory(tablesWorkspace);

		} catch (Exception e) {
			success = false;

			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			Workbench.getInstance().error(e);
			e.printStackTrace();
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
	
	private String importWorkspaceFolder() throws Exception {

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
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			throw new Exception("workspace folder not found");
		}

		if(destWorkspaceName==null || destWorkspaceName.isEmpty()) {
			checkName(workspaceName);
		}
		
		logger.info("Starting the ws folder files import...");
		String cpy = importWS.concat(workspaceName).concat("/");
		File copy = new File(cpy);
		String pst = FileUtils.getWorkspacesFolderPath().concat(destWorkspaceName).concat("/");
		File paste = new File(pst);

		this.destPath = paste.getAbsolutePath();
		
		org.apache.commons.io.FileUtils.copyDirectory(copy, paste);

		File folderDelete = new File(unzippedPath);
		org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);

		return workspaceName;
	}
	
	public void checkIfOverride(boolean override) {
		this.override = override;
	}


	public void checkName(String name) throws Exception {
		
		if(name!=null && !name.isEmpty()) {
			
			List<String> databases = DatabaseServices.getDatabasesAvailable();
			
			if(databases.contains(name) && !this.override)
				throw new Exception("a database named '" + name + "' already exists, please select a different name!");
			else
				this.destWorkspaceName = name;
		}
		else
			this.destWorkspaceName = null;
	}
	
	/**
	 * 
	 */
	@Cancel
	public void cancel() {

		String[] options = new String[2];
		options[0]="yes";
		options[1]="no";

		int result=CustomGUI.stopQuestion("cancel confirmation", "are you sure you want to cancel the operation?", options);

		if(result==0) {
			
			progress.setTime((GregorianCalendar.getInstance().getTimeInMillis()-GregorianCalendar.getInstance().getTimeInMillis()),1,1);
			DatabaseServices.setCancelExporterBatch(this.destWorkspaceName, true);
			logger.warn("export workspace operation canceled!");
			Workbench.getInstance().warn("Please hold on. Your operation is being cancelled.");
		}
	}

	/**
	 * @return
	 */
	@Progress(progressDialogTitle = "import workspace", modal = false, workingLabel = "importing workspace", preferredWidth = 400, preferredHeight=300)
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
