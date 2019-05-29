package pt.uminho.ceb.biosystems.merlin.merlin_exporter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uvigo.ei.aibench.core.operation.annotation.Direction;
import es.uvigo.ei.aibench.core.operation.annotation.Operation;
import es.uvigo.ei.aibench.core.operation.annotation.Port;
import es.uvigo.ei.aibench.workbench.Workbench;
import pt.uminho.ceb.biosystems.merlin.aibench.datatypes.WorkspaceAIB;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.LoadFromConf;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.MerlinUtils;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.Connection;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.DatabaseSchemas;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.Enumerators.DatabaseType;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;

/**
 * @author claudia
 *
 */

@Operation(name="import workspace", description="Import the workspace backup.")
public class RestoreDatabase {
	private String databaseType;
	private String zipPath;
	private String exportDbType;
	private String unzippedPath;
	private String fileDate;
	private String workspaceName;
	private String importWS;
	private Boolean success = true;
	private WorkspaceAIB project;
	private String destWorkspaceName;
	private Connection connection;
	final static Logger logger = LoggerFactory.getLogger(RestoreDatabase.class);
	
	@Port(name="workspace:",description="select workspace",direction=Direction.INPUT,order=1)
	public void setProject(WorkspaceAIB project){
		this.project = project;
		
		this.connection = new Connection(project.getDatabase().getDatabaseAccess());
       
		destWorkspaceName = this.project.getName();
	}
	

	/**
	 * @param directory
	 * @throws IOException 
	 */
	@Port(direction=Direction.INPUT, name="Directory:",description=".mer folder",validateMethod="checkDirectory",order=3)
	public void selectDirectory(File directory) throws IOException{
		try {
			Map<String, String> credentials = LoadFromConf.loadDatabaseCredentials(FileUtils.getConfFolderPath());
			this.databaseType = credentials.get("dbtype");
			unzippedPath = FileUtils.getHomeFolderPath().concat("ws").concat("/").concat(destWorkspaceName).concat("/");
			
			unzippedPath = unzippedPath + "backup_ws_"+fileDate;
			
			logger.info("Starting the .mer folder unzip..." );
			FileUtils.extractZipFile(zipPath, unzippedPath);

			checkExportDbType();
			importWorkspaceFolder();
			
			switch(this.databaseType) {
			
				case "h2":
					if(exportDbType.equals("H2")) {
						//H2 to H2
						importH2ToH2();
					}else {
						//MySQL to H2
						Workbench.getInstance().error("Impossible to import from MySQL to H2 database.");
						
					}
					break;
					
				case "mysql":
					if(exportDbType.equals("MySQL")) {
						//MySQL to MySQL
						if (validateDefaultCredentials() )
							importMySQLToMySQL();
						else {
							Workbench.getInstance().error("Please configure your MySQL credentials first!");
							success = false;
							
						}
							
					}else {
						//H2 to MySQL
						Workbench.getInstance().error("Impossible to import from H2 to MySQL database.");
						success = false;
					}
					break;
			}
			
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			
		} catch (Exception e) {
			success = false;
			
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			
			e.printStackTrace();
			throw new IllegalArgumentException("Error while importing.");
		}

		if(success)
			Workbench.getInstance().info("Workspace "  + workspaceName + " successfully imported into workspace" + destWorkspaceName + ".");
			logger.info("Workspace " + workspaceName + " successfully imported.");

	}

	private void importMySQLToMySQL() throws Exception {
		
		Map<String, String> credentials = LoadFromConf.loadDatabaseCredentials(FileUtils.getConfFolderPath());
		String username = null, password = null, host = null, port = null;
		
		DatabaseType dbType = DatabaseType.H2;
		if (credentials.get("dbtype").equals("mysql"))
			dbType = DatabaseType.MYSQL;
		
		username = credentials.get("username");
		password = credentials.get("password");
		if (dbType.equals(DatabaseType.MYSQL)) {
			host = credentials.get("host");
			port = credentials.get("port");
		}
		
		DatabaseSchemas schemas = new DatabaseSchemas(username, password, host, port, dbType);

		String filePath=importWS+"backup.sql";
		
		try {
			logger.info("Starting the MySQL workspace " + workspaceName + " import...");

			if(schemas.cleanSchema(destWorkspaceName, filePath)) {
				
				MerlinUtils.updateAllViews(destWorkspaceName);

			}
			else {				
				Workbench.getInstance().error("There was an error when trying to format "+ destWorkspaceName +"!!");
				File folderDelete = new File(unzippedPath);
				org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
				success = false;
			}
		} catch (Exception e) {
			Workbench.getInstance().error("There was an error when trying to format "+ destWorkspaceName +"!!");
			e.printStackTrace();
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			success = false;
		}
	}

	private void importH2ToH2() throws IOException {
		
		try {
			
			logger.info("Starting the H2 workspace " + workspaceName + " import...");
			
			connection.createStatement().execute("SHUTDOWN");  //to delete the .lock file
			connection.closeConnection();

			String mv = importWS.concat(workspaceName).concat(".mv").concat(".db");
			String trace = importWS.concat(workspaceName).concat(".trace").concat(".db");
			File fileMv = new File(mv);
			File fileTrace = new File(trace);
			String destinationMv = FileUtils.getHomeFolderPath().concat("h2Database").concat("/").concat(destWorkspaceName).concat(".mv").concat(".db");
			String destinationTrace = FileUtils.getHomeFolderPath().concat("h2Database").concat("/").concat(destWorkspaceName).concat(".trace").concat(".db");
			File destMv = new File(destinationMv);
			File destTrace = new File(destinationTrace);
						
			if(fileMv != null) {
				org.apache.commons.io.FileUtils.copyFile(fileMv, destMv);
			}
			
			if(fileTrace != null) {
				org.apache.commons.io.FileUtils.copyFile(fileTrace, destTrace);
			}
				
			
		}
		catch(Exception e) {
			System.out.println("Error while importing H2 to H2");
			Workbench.getInstance().warn("Error while importing H2 files!!");
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			success = false;
		}
		
	}

	/**
	 * @param directory
	 */
	
	public void checkDirectory(File directory) {

		if(directory == null || directory.toString().isEmpty()) {
			success = false;
			throw new IllegalArgumentException("Please select a .mer file!");
		}
		else {
			try {
				zipPath = directory.getPath();
				LocalDateTime currentTime = LocalDateTime.now();
		        
		        fileDate = currentTime.getHour() + "h" + currentTime.getMinute() + "m" + currentTime.getSecond() + "s"
		                + currentTime.getDayOfMonth() + currentTime.getMonthValue() + currentTime.getYear();
				String extension;

				extension = FileUtils.getFileExtension(directory);
				if(extension.equals(null) || !extension.equals("mer")){
					success=false;
					throw new IllegalArgumentException("Please select a .mer file!");
				}

			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				success=false;
				e.printStackTrace();
				throw new IllegalArgumentException("Please select a .mer file!");
			}

		}
	}
	
	public void checkExportDbType() throws IOException {
		logger.info("Getting exported database type...");
		List <String> foldercontent = FileUtils.getFilesFromFolder(unzippedPath, false);
		String folder = foldercontent.get(0);
		importWS = unzippedPath.concat("/").concat(folder).concat("/");
		List <String> finalFolder = FileUtils.getFilesFromFolder(importWS, false);
		
		
		String path = zipPath;
		String[] finalPath = path.split(".mer");
		path = finalPath[0];
		exportDbType = "";
		
		try {
			for (String fileName : finalFolder) {
				String dbType = FileUtils.getFileExtension(fileName);
				if (dbType.equals("sql")) {
					exportDbType = "MySQL";
					break;
				}	
				else if (dbType.equals("db")){
					exportDbType = "H2";
					break;
				}

			}
			
			if(exportDbType.equals("")){
				success = false;
				Workbench.getInstance().error("Files not found in selected directory!");
			}
			
		} catch (Exception e) {
			success = false;
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
			e.printStackTrace();
			Workbench.getInstance().error("Files not found in selected directory!");

		}

	}
	
	private void importWorkspaceFolder() throws IOException {
		List <String> foldercontent = FileUtils.getFilesFromFolder(unzippedPath, false);
		String folder = foldercontent.get(0);
		importWS = unzippedPath.concat("/").concat(folder).concat("/");
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
			Workbench.getInstance().error("Workspace folder not found");
		}
		
		try {
			logger.info("Starting the ws folder files import...");
			String cpy = importWS.concat(workspaceName).concat("/");
			File copy = new File(cpy);
			String pst = FileUtils.getHomeFolderPath().concat("ws").concat("/").concat(destWorkspaceName).concat("/");
			File paste = new File(pst);
			
			org.apache.commons.io.FileUtils.copyDirectory(copy, paste);
			
		} catch (Exception e) {
			success = false;
			File folderDelete = new File(unzippedPath);
			org.apache.commons.io.FileUtils.deleteDirectory(folderDelete);
		}
		
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
	
}
