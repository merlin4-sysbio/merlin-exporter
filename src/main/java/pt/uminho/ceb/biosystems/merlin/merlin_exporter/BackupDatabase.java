package pt.uminho.ceb.biosystems.merlin.merlin_exporter;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uvigo.ei.aibench.core.operation.annotation.Direction;
import es.uvigo.ei.aibench.core.operation.annotation.Operation;
import es.uvigo.ei.aibench.core.operation.annotation.Port;
import es.uvigo.ei.aibench.workbench.Workbench;
import pt.uminho.ceb.biosystems.merlin.aibench.datatypes.WorkspaceAIB;
import pt.uminho.ceb.biosystems.merlin.dataAccess.InitDataAccess;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;


/**
 * @author claudia
 *
 */
@Operation(name="export workspace", description="Make a workspace backup.")
public class BackupDatabase {
	
	
	private String databaseName;
	private String initialPath;
	private String name = "";
	private File directory;
	final static Logger logger = LoggerFactory.getLogger(BackupDatabase.class);

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
			backupWorkspaceFolder();
			
			String backupXmlTables = this.directory.getAbsolutePath().concat("/"+this.databaseName).concat("/tables/");
			
			File newFile = new File(backupXmlTables);
			
			if(!newFile.exists())
				newFile.mkdirs();
			
			InitDataAccess.getInstance().getDatabaseExporterBatchService(this.databaseName).dbtoXML(backupXmlTables);
			
			InitDataAccess.getInstance().dropExporterConnection(this.databaseName);
			
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
		String destiny;
		
		path = FileUtils.getWorkspaceFolderPath(databaseName);
		destiny = this.directory+"/"+databaseName;
		new File(this.directory+"/"+databaseName).mkdirs();
		
		File p = new File(path);
		File d = new File(destiny);
		
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
}
	