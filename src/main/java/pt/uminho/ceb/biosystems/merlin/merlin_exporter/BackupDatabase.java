package pt.uminho.ceb.biosystems.merlin.merlin_exporter;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
	private WorkspaceAIB project = null;
	private File directory;
	private long taxID;
	private Boolean validCredentials = true;
	private Map<String, String> credentials = LoadFromConf.loadDatabaseCredentials(FileUtils.getConfFolderPath());
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
			this.databaseName = project.getDatabase().getDatabaseName();
			this.taxID = project.getTaxonomyID();
			this.project = project;

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
			
			if (credentials.get("dbtype").equals("mysql")) {
				backupsqltosql();
//				backupsqltoh2();
			}
			else 
				backupdbH2();
			
			if (validCredentials) {
				zipBackupFiles();
				Workbench.getInstance().info("Workspace successfully exported as " + name + ".");
				logger.info("Workspace successfully exported as " + name);
			}

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
		
		if (validateDefaultCredentials()) {
			
			path = FileUtils.getWorkspaceFolderPath(databaseName);
			destiny = this.directory+"/"+databaseName;
			new File(this.directory+"/"+databaseName).mkdirs();
			
			File p = new File(path);
			File d = new File(destiny);
			
			org.apache.commons.io.FileUtils.copyDirectory(p, d);
			
		}
		
		else
			org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
			return;
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
	
	public void backupsqltosql() throws IOException {

		try {
			
			logger.info("Starting the SQL database backup...");
			
			if (validateDefaultCredentials()) {
				
				String username = null, password = null, host = null, port = null, database = null;
				
				username = credentials.get("username");
				password = credentials.get("password");
				host = credentials.get("host");
				port = credentials.get("port");
				
				
				database = project.getDatabase().getDatabaseName();
				
				dump(host, port, database, username, password, new ArrayList<>());
				
			} else {
				Workbench.getInstance().error("Please configure your MySQL credentials first!");
				org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
				
			}
				
		} 
		catch (Exception e) {
			e.printStackTrace();
			org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
		}

	}
	
	   
	public void dump(String host, String port, String database, String username, String password, List<String> tables) throws IOException { 

		try {

			String folderPath = this.directory.getPath();	

			File f1 = new File(folderPath);
			f1.mkdir();

			String savePath = folderPath + "/backup.sql";
			
			String aux = "";
			
//			BACKUP SQL TO SQL 
			String homeFolderPath = FileUtils.getHomeFolderPath().concat("utilities").concat("/");
			String executeCmd = homeFolderPath + "mysqldump.exe --add-drop-table --user="
					+ username + " --password=" + password + " --host=" + host + " --port=" + port + " " + database
					+ " " + aux + " -r " + savePath;
			
			Process runtimeProcess = Runtime.getRuntime().exec(executeCmd);
			int processComplete = runtimeProcess.waitFor();

			
			if (processComplete == 0) {
				System.out.println("Backup Complete");
				
			} 
			else {
				System.out.println("Backup Failure");
				Workbench.getInstance().error("Error while exporting");
				org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
				return;
			}

		} 
		catch (Exception e) {
			e.printStackTrace();
			Workbench.getInstance().error("Error while exporting");
			org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
			return;
			
		}

	}
	
	public void backupdbH2() throws IOException {
		File homeFile = FileUtils.getHomeFolder();
		String path = homeFile.getPath();
		String src;
		String dest;
		File source;
		File destiny;
		
		
		List<String> extensions = new ArrayList<>();
		extensions.add(".mv.db");
		extensions.add(".trace.db");
		
		
		try {
			logger.info("Starting the H2 database backup...");
			
			for(String extension : extensions){
				src = path+"/h2Database/"+this.databaseName + extension;
				dest = this.directory+"/"+this.databaseName + extension;
				
				source = new File(src);
				destiny = new File(dest);
				
				if (source.exists()) {
					org.apache.commons.io.FileUtils.copyFile(source,destiny);
				} else {
					continue;
				}
			}
			
			
		}catch (Exception e) {
			e.printStackTrace();
			Workbench.getInstance().error("Error while exporting");
			org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
			return;
			
		}
		
		
	}

//	public void backupsqltoh2() throws IOException {
//		
//		try {
//			
//			logger.info("Starting the SQL to H2 backup...");
//
//			String username = null, password = null, database = null, host = null, port = null, aux = null;
//
//			username = credentials.get("username");
//			password = credentials.get("password");
//			host = credentials.get("host");
//			port = credentials.get("port");
//			aux = "";
//
//			database = project.getDatabase().getDatabaseName();
//
//			String folderPath = this.directory.getPath();	
//
//			File f1 = new File(folderPath);
//			f1.mkdir();
//
//			String savePath = folderPath + "/backupH2.sql";
//
//			String executeCmd = "mysqldump --compatible=ansi,no_table_options,no_field_options,no_key_options --hex-blob --skip-opt --user="
//					+ username + " --password=" + password + " --host=" + host + " --port=" + port + " " + database
//					+ " " + aux + " -r " + savePath;
//			
//			System.out.println(executeCmd);
//
//			Process runtimeProcess = Runtime.getRuntime().exec(executeCmd);
//			int processComplete = runtimeProcess.waitFor();
//
//			if (processComplete == 0) {
//				System.out.println("Backup Complete");
//			} 
//			else {
//				System.out.println("Backup Failure");
//				org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
//			}
//			
//			List<String> tables = new ArrayList<String>(); //tables is an empty list
//
//			rewriteFile(savePath, tables);  
//			
//			File saveFile = new File(savePath);
//			saveFile.delete();
//		} 
//		catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			org.apache.commons.io.FileUtils.deleteDirectory(this.directory);
//
//		}
//
//	}
	
//	public static void rewriteFile(String path, List<String> tables) throws IOException {
//
//		File file = new File(path);
//
//		BufferedWriter writer = new BufferedWriter(new FileWriter(file.getParent().concat("/").concat("backupSQL_H2.sql"))); 
//
//		writer.write("DROP VIEW IF EXISTS `reactions_view_noPath_or_noEC`;");
//		writer.newLine();
//		writer.write("DROP TABLE IF EXISTS `reactions_view_noPath_or_noEC`;");
//		writer.newLine();
//		writer.write("DROP VIEW IF EXISTS `reactions_view`;");
//		writer.newLine();
//		writer.write("DROP TABLE IF EXISTS `reactions_view`;");
//		writer.newLine();
//
//		for(String table : tables) {
//			writer.write("DROP TABLE IF EXISTS `" + table + "`;");
//			writer.newLine();
//		}
//
//		writer.newLine();
//
//		BufferedReader br = new BufferedReader(new FileReader(file)); 
//
//		String st; 
//		while ((st = br.readLine()) != null) {
//			writer.write(st);
//			writer.newLine();
//		} 
//
//		writer.newLine();
//
//		writer.write("CREATE  OR REPLACE VIEW reactions_view_noPath_or_noEC AS\r\n" + 
//				"SELECT DISTINCT idreaction, reaction.name AS reaction_name , equation, reversible, pathway.idpathway, pathway.name AS pathway_name , inModel, isGeneric, reaction.source, originalReaction, compartment_idcompartment , notes\r\n" + 
//				"FROM reaction\r\n" + 
//				"LEFT JOIN pathway_has_reaction ON idreaction=pathway_has_reaction.reaction_idreaction\r\n" + 
//				"LEFT JOIN pathway ON pathway.idpathway=pathway_has_reaction.pathway_idpathway\r\n" + 
//				"WHERE (idpathway IS NULL ) \r\n" + 
//				"ORDER BY pathway.name,  reaction.name;");
//
//		writer.newLine();
//
//		writer.write("CREATE  OR REPLACE VIEW reactions_view AS\r\n" + 
//				"SELECT DISTINCT idreaction, reaction.name AS reaction_name, equation, reversible, pathway.idpathway, pathway.name AS pathway_name, reaction.inModel, isGeneric, reaction.source, originalReaction, compartment_idcompartment, notes\r\n" + 
//				"FROM reaction\r\n" + 
//				"INNER JOIN pathway_has_reaction ON idreaction=pathway_has_reaction.reaction_idreaction\r\n" + 
//				"INNER JOIN pathway ON pathway.idpathway=pathway_has_reaction.pathway_idpathway\r\n" + 
//				"WHERE pathway_has_reaction.pathway_idpathway=pathway.idpathway\r\n" + 
//				"ORDER BY pathway.name, reaction.name;");
//
//		writer.close();
//		br.close();
//
//	}
	
	private Boolean validateDefaultCredentials() {
		String username = null, password = null, host = null, port = null;
		
		username = credentials.get("username");
		password = credentials.get("password");
		host = credentials.get("host");
		port = credentials.get("port");
		
		validCredentials = !(username.equals("your_username") && password.equals("your_password") && host.equals("your_ip_address") && port.equals("3306"));
		
		return validCredentials;	
		
	}

}
	