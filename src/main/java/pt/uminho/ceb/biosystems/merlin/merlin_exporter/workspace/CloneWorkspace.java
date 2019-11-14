package pt.uminho.ceb.biosystems.merlin.merlin_exporter.workspace;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uvigo.ei.aibench.core.Core;
import es.uvigo.ei.aibench.core.ParamSpec;
import es.uvigo.ei.aibench.core.operation.OperationDefinition;
import es.uvigo.ei.aibench.core.operation.annotation.Cancel;
import es.uvigo.ei.aibench.core.operation.annotation.Direction;
import es.uvigo.ei.aibench.core.operation.annotation.Operation;
import es.uvigo.ei.aibench.core.operation.annotation.Port;
import es.uvigo.ei.aibench.core.operation.annotation.Progress;
import es.uvigo.ei.aibench.workbench.Workbench;
import pt.uminho.ceb.biosystems.merlin.aibench.datatypes.WorkspaceAIB;
import pt.uminho.ceb.biosystems.merlin.aibench.gui.CustomGUI;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.TimeLeftProgress;
import pt.uminho.ceb.biosystems.merlin.core.datatypes.Workspace;
import pt.uminho.ceb.biosystems.merlin.services.DatabaseServices;
import pt.uminho.ceb.biosystems.merlin.services.ProjectServices;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;

@Operation(name="Merge Compartments", description = "Merge Compartments")
public class CloneWorkspace implements PropertyChangeListener  {

	private String newWorkspaceName;
	private String directory;
	private WorkspaceAIB workspace;
	private Integer dataSize = 1;
	private String destPath;
	private long startTime;
	private String message;
	private TimeLeftProgress progress = new TimeLeftProgress();
	private boolean override = false;
	private Boolean success = true;
	private AtomicBoolean cancel = new AtomicBoolean(false);

	final static Logger logger = LoggerFactory.getLogger(CloneWorkspace.class);

	@Port(direction=Direction.INPUT, name="force database creation", description="this command forces merlin to create a database with the seleted name. If a database with such name already exists, it will be replaced",
			advanced = true, validateMethod="checkOverride", defaultValue = "false", order=1)
	public void setOldWorkspaceName(boolean override) {}


	@Port(direction=Direction.INPUT, name="workspace", description="name of the workspace to clone", validateMethod="checkNewProject", order=2)
	public void setWorkspace (WorkspaceAIB workspace){}

	@Port(direction=Direction.INPUT, name="workspace new name",  description="new workspace's name",  validateMethod="checkIfValidName", order=3)
	public void setWorkspaceNewName (String name){

		try {
			this.startTime = GregorianCalendar.getInstance().getTimeInMillis();

			String tempDirectory = FileUtils.getCurrentTempDirectory().concat(this.workspace.getName()).concat("/");

			File tempFile = new File(tempDirectory);

			if(tempFile.exists())
				org.apache.commons.io.FileUtils.deleteDirectory(tempFile);

			this.directory = tempDirectory;

			tempDirectory = tempDirectory.concat("/tables/");

			tempFile = new File(tempDirectory);

			tempFile.mkdirs();

			backupWorkspaceFolder();

			this.message = "exporting data...";
			logger.info(this.message);

			DatabaseServices.databaseToXML(this.workspace.getName(), tempFile.getAbsolutePath().concat("/"), this);

			//			zipBackupFiles();

			try {

				//			unzippedPath = FileUtils.getHomeFolderPath().concat("ws").concat("/").concat(destWorkspaceName).concat("/");
				//				unzippedPath = FileUtils.getHomeFolderPath().concat("temp").concat("/").concat("importWorkspaceTemp").concat("/");
				//
				//				logger.info("starting the .mer folder unzip..." );
				//				FileUtils.extractZipFile(zipPath, unzippedPath);

				importWorkspaceFolder();

				this.message = "loading data";

				DatabaseServices.readxmldb(this.newWorkspaceName, tempFile.getAbsolutePath().concat("/"), this.cancel, this);

				File folderDelete = new File(this.directory);
				org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);


			} catch (Exception e) {
				success = false;

				Workbench.getInstance().error(e);
				e.printStackTrace();
			}

			if(success) {

				Integer taxId = ProjectServices.getOrganismID(this.newWorkspaceName);

				if(taxId != null) {

					ParamSpec[] paramsSpec = new ParamSpec[]{
							new ParamSpec("Database",String.class,this.newWorkspaceName,null),
							new ParamSpec("TaxonomyID",long.class,Long.parseLong(taxId.toString()),null)	
					};

					for (@SuppressWarnings("rawtypes") OperationDefinition def : Core.getInstance().getOperations()){
						if (def.getID().equals("operations.NewWorkspace.ID")){

							Workbench.getInstance().executeOperation(def, paramsSpec);
						}
					}
				}
				
				Workbench.getInstance().info("Workspace successfully cloned as '" + name + "'.");
				logger.info("Workspace successfully cloned as " + name);
			}



		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error while exporting.");
		}


	}

	private void backupWorkspaceFolder() throws IOException {

		logger.info("Copying workspace folder files...");
		String path;
		String destination;

		path = FileUtils.getWorkspaceFolderPath(this.workspace.getName());
		destination = this.directory;
		new File(destination).mkdirs();

		File p = new File(path);
		File d = new File(destination);

		org.apache.commons.io.FileUtils.copyDirectory(p, d);

	}

	private void importWorkspaceFolder() throws Exception {

		List <String> foldercontent = FileUtils.getFilesFromFolder(this.directory, false);
		String folder = foldercontent.get(0);
		String importWS = this.directory.concat(folder);
		//		List <String> finalFolder = FileUtils.getFilesFromFolder(importWS, false);
		//		workspaceName = "";
		//		for (String file : finalFolder) {
		//			String extension = FileUtils.getFileExtension(file);
		//			if(extension.equals("")) { //If the extension is empty, it means that it is a folder. Since there is only one folder, it is the workspace folder.
		//
		//				workspaceName = file;
		//				break;
		//			}	
		//		}
		//
		//		if(workspaceName.equals("")) {
		//			File folderDelete = new File(unzippedPath);
		//			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
		//			throw new Exception("workspace folder not found");
		//		}
		//
		//		if(destWorkspaceName==null || destWorkspaceName.isEmpty()) {
		//			checkName(workspaceName);
		//		}

		logger.info("Starting the ws folder files import...");
		//		String cpy = importWS.concat(this.workspace.getName()).concat("/");
		File copy = new File(importWS);
		String pst = FileUtils.getWorkspacesFolderPath().concat(newWorkspaceName);

		File paste = new File(pst);

		if(paste.exists())
			org.apache.commons.io.FileUtils.deleteDirectory(paste);

		this.destPath = paste.getAbsolutePath();

		pst = pst.concat("/").concat(folder);
		paste = new File(pst);



		org.apache.commons.io.FileUtils.copyDirectory(copy, paste);

	}


	//////////////////////////ValidateMethods/////////////////////////////
	/**
	 * @param project
	 */
	public void checkNewProject(WorkspaceAIB workspace) {

		if(workspace == null) {

			throw new IllegalArgumentException("no workspace selected!");
		}
		else {

			this.workspace = workspace;

		}
	}

	/**
	 * @param project
	 */
	public void checkOverride(boolean override) {

		this.override = override;
	}


	public void checkIfValidName(String name) throws Exception {

//		try {
			if(name == null || name.isEmpty())
				throw new Exception("please insert a valid name");
			else {

				List<String> names = DatabaseServices.getDatabasesAvailable();

				if(names.contains(name) && !this.override)
					throw new Exception("workspace name already in use, please select a different name! "
							+ "If you wish to override an existing database, please select 'force database creation' at this operations' menu.");
				else
					this.newWorkspaceName = name;
			}
//		} 
//		catch (Exception e) {
//			Workbench.getInstance().error(e);
//			e.printStackTrace();
//		}

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
			DatabaseServices.setCancelExporterBatch(this.workspace.getName(), true);
			logger.warn("export workspace operation canceled!");
			Workbench.getInstance().warn("Please hold on. Your operation is being cancelled.");
		}
	}

	/**
	 * @return
	 */
	@Progress(progressDialogTitle = "clone workspace", modal = false, workingLabel = "cloning workspace", preferredWidth = 400, preferredHeight=300)
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
