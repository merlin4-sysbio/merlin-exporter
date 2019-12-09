package pt.uminho.ceb.biosystems.merlin.merlin_exporter.workspace;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.GregorianCalendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uvigo.ei.aibench.core.operation.annotation.Cancel;
import es.uvigo.ei.aibench.core.operation.annotation.Direction;
import es.uvigo.ei.aibench.core.operation.annotation.Operation;
import es.uvigo.ei.aibench.core.operation.annotation.Port;
import es.uvigo.ei.aibench.core.operation.annotation.Progress;
import es.uvigo.ei.aibench.workbench.Workbench;
import pt.uminho.ceb.biosystems.merlin.gui.datatypes.WorkspaceAIB;
import pt.uminho.ceb.biosystems.merlin.gui.jpanels.CustomGUI;
import pt.uminho.ceb.biosystems.merlin.gui.utilities.TimeLeftProgress;
import pt.uminho.ceb.biosystems.merlin.services.DatabaseServices;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;

@Operation(name="export workspace", description="Make a workspace backup.")
public class BackupDatabase implements PropertyChangeListener  {
	
	
	private String databaseName;
	private String initialPath;
	private String name = "";
	private File directory;
	final static Logger logger = LoggerFactory.getLogger(BackupDatabase.class);
	private long startTime;
	private String message;
	private int dataSize;
	public TimeLeftProgress progress = new TimeLeftProgress();

	@Port(direction=Direction.INPUT, name="Select Workspace", validateMethod = "checkProject", order=1)
	public void setProject(WorkspaceAIB project) {
	
	}

	/**
	 * @param project
	 */
	public void checkProject(WorkspaceAIB project) {

		if(project==null) {

			throw new IllegalArgumentException("Please select a workspace.");
		}
		else {
			this.databaseName = project.getName();

		} 
	}

	/**
	 * @param directory
	 * @throws IOException 
	 */
	@Port(direction=Direction.INPUT, name="Directory:",description="folder",validateMethod="checkDirectory",order=3)
	public void selectDirectory(File directory){

		try {
			this.startTime = GregorianCalendar.getInstance().getTimeInMillis();
			
			backupWorkspaceFolder();
			
			String backupXmlTables = this.directory.getAbsolutePath().concat("/"+this.databaseName).concat("/tables/");
			
			File newFile = new File(backupXmlTables);
			
			if(!newFile.exists())
				newFile.mkdirs();
			
			this.message = "exporting data...";
			logger.info(this.message);
			
			DatabaseServices.databaseToXML(this.databaseName, backupXmlTables, this);
			
			zipBackupFiles();
			Workbench.getInstance().info("Workspace successfully exported as " + name + ".");
			logger.info("Workspace successfully exported as " + name);

		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error while exporting.");
		}

	}
	

	private void zipBackupFiles() throws IOException {
		
		logger.info("Creating the .mer to export...");

		String path = this.directory.getPath();
		String extension = ".mer";
		LocalDateTime currentTime = LocalDateTime.now();
        String date = "_" + currentTime.getHour() + "h" + currentTime.getMinute() + "m" + currentTime.getSecond() + "s"
                + currentTime.getDayOfMonth() + currentTime.getMonthValue() + currentTime.getYear();
		name = "backup_ws_" + databaseName + date + extension;

		if(FileUtils.existsPath(path)){

			FileUtils.createZipFile(path, this.initialPath + "/" + name, 1);

		}
		else
			throw new IllegalArgumentException("Error while exporting.");
		
		org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
	}

	private void backupWorkspaceFolder() throws IOException {
		
		logger.info("Copying workspace folder files...");
		String path;
		String destination;
		
		path = FileUtils.getWorkspaceFolderPath(databaseName);
		destination = this.directory+"/"+databaseName;
		new File(this.directory+"/"+databaseName).mkdirs();
		
		File p = new File(path);
		File d = new File(destination);
		
		org.apache.commons.io.FileUtils.copyDirectory(p, d);
			
	}

	/**
	 * @param directory
	 */
	
	public void checkDirectory(File directory) {

		if(directory == null || directory.toString().isEmpty()) {

			throw new IllegalArgumentException("Please select a directory!");
		}
		else {
			
			LocalDateTime currentTime = LocalDateTime.now();
			String date = "_" + currentTime.getHour() + "h" + currentTime.getMinute() + "m" + currentTime.getSecond() + "s"
					+ currentTime.getDayOfMonth() + currentTime.getMonthValue() + currentTime.getYear();

			if(directory.isDirectory()) {
				String path = directory.getPath();
				this.initialPath = path;
				
				
				String newPath = path+"/backup_ws" + date;
				File newDir = new File(newPath);
				this.directory = newDir;
			}
			else {
				throw new IllegalArgumentException("Please select a directory!");
			}

		}
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
			DatabaseServices.setCancelExporterBatch(this.databaseName, true);
			logger.warn("export workspace operation canceled!");
			Workbench.getInstance().warn("Please hold on. Your operation is being cancelled.");
		}
	}
	
	/**
	 * @return
	 */
	@Progress(progressDialogTitle = "export workspace", modal = false, workingLabel = "exporting workspace", preferredWidth = 400, preferredHeight=300)
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
	