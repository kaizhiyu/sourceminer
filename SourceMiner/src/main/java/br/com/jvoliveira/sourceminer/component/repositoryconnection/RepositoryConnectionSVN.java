/**
 * 
 */
package br.com.jvoliveira.sourceminer.component.repositoryconnection;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import br.com.jvoliveira.arq.utils.StringUtils;
import br.com.jvoliveira.sourceminer.domain.Project;
import br.com.jvoliveira.sourceminer.domain.ProjectConfiguration;
import br.com.jvoliveira.sourceminer.domain.RepositoryConnector;
import br.com.jvoliveira.sourceminer.domain.RepositoryItem;
import br.com.jvoliveira.sourceminer.domain.RepositoryRevision;
import br.com.jvoliveira.sourceminer.domain.RepositoryRevisionItem;
import br.com.jvoliveira.sourceminer.domain.enums.CommitType;
import br.com.jvoliveira.sourceminer.exceptions.RepositoryConnectionException;
import br.com.jvoliveira.sourceminer.exceptions.RepositoryFileContentNotFoundException;

/**
 * @author Joao Victor
 *
 */
public class RepositoryConnectionSVN implements RepositoryConnection{

	private RepositoryConnector connector;
	private SVNRepository repository;
	
	private RepositoryParseSVN parse = new RepositoryParseSVN();
	
	public RepositoryConnectionSVN(){
		
	}
	
	public RepositoryConnectionSVN(RepositoryConnector connector){
		this.connector = connector;
	}
	
	@Override
	public RepositoryConnector getConnector() {
		return this.connector;
	}

	@Override
	public void setConnector(RepositoryConnector connector) {
		this.connector = connector;
	}
	
	@Override
	public RepositoryParse getParse() {
		return this.parse;
	}
	
	@Override
	public boolean testConnection() throws RepositoryConnectionException{
		try{
			openConnection();
			SVNDirEntry entry = repository.getDir("", -1, false, null);
		} catch(SVNAuthenticationException authException){
			throw new RepositoryConnectionException("Falha na autenticação. Verifique credenciais de acesso ao repositório");
		}catch (Exception e) {
			return false;
		}finally{
			if(isConnectionOpened())
				closeConnection();
		}
		
		return true;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void openConnection() throws RepositoryConnectionException {
		try {
			if(connector.getRepositoryLocation().getLocationType().isLocal())
				FSRepositoryFactory.setup();
			else if(connector.getRepositoryLocation().getLocationType().isRemote())
				DAVRepositoryFactory.setup();
			
			repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(this.connector.getRepositoryLocation().getUrl()));
			ISVNAuthenticationManager authManager;
			if(connector.isAnonymousConnection())
				authManager = SVNWCUtil.createDefaultAuthenticationManager();
			else{
				authManager = 
						  new BasicAuthenticationManager(new SVNAuthentication[] {
						        new SVNPasswordAuthentication(connector.getUsername(), connector.getPassword(), 
						                                      false, SVNURL.parseURIEncoded(this.connector.getRepositoryLocation().getUrl()), false),
						  });
			}
			
			repository.setAuthenticationManager(authManager);
			
		} catch(SVNAuthenticationException authException){
			throw new RepositoryConnectionException("Falha na autenticação. Verifique credenciais de acesso ao repositório");
		}catch (SVNException e) {
			throw new RepositoryConnectionException("Não foi possível estabelecer conexão com o repositório");
		}
	}

	@Override
	public List<RepositoryItem> getAllProjectItens(Project project) {
		String path = project.getPath();
		List<RepositoryItem> repositoryItens = new ArrayList<RepositoryItem>();
		
		listEntries(path,repositoryItens);
		
		return repositoryItens;
	}
	
	@SuppressWarnings("unchecked")
	private void listEntries(String path, List<RepositoryItem> listEntries) {
		Collection<SVNDirEntry> entries;
		try {
			entries = repository.getDir( path, -1 , null , (Collection<SVNDirEntry>) null );
			
			Iterator<SVNDirEntry> iterator = entries.iterator( );
			
			while ( iterator.hasNext( ) ) {
				SVNDirEntry entry = iterator.next( );
				
				if ( entry.getKind() == SVNNodeKind.DIR ) {
					listEntries( ( path.equals( "" ) ) ? entry.getName( ) : path + "/" + entry.getName( ), listEntries );
				}else if(entry.getKind() == SVNNodeKind.FILE && entry.getName().contains(".java")){
					RepositoryItem repositoryItem = parse.parseDirEntryToRepositoryItem(entry, path);
					if(!listEntries.contains(repositoryItem)){
						listEntries.add(repositoryItem);
						//TODO: Exibir na interface através do objeto
						System.out.println("ARTEFATO ENCONTRADO: " + repositoryItem.getFullPath());
					}
				}
				
				
			}
		} catch (SVNException e) {
			e.printStackTrace();
			System.err.println("Não foi possível acessar o caminho especificado");
		}
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public List<RepositoryRevisionItem> getRevisionItensInProjectRange(Project project, ProjectConfiguration config){
		List<RepositoryRevisionItem> revisionItemLogs = new ArrayList<RepositoryRevisionItem>();
		Integer syncStartRevision = null;
		Integer syncEndRevision = null;
		if(config.getSyncStartRevision() != null && !config.getSyncStartRevision().trim().equals(""))
				syncStartRevision = new Integer(config.getSyncStartRevision());
		else
				syncStartRevision = 0;
		if(config.getSyncEndRevision() != null && !config.getSyncEndRevision().trim().equals(""))
			syncEndRevision = new Integer(config.getSyncEndRevision());
		else
			syncEndRevision = -1;
		
		Collection<SVNLogEntry> entries = getSVNLogEntryByRevision(project.getPath(), syncStartRevision, syncEndRevision, !config.isJoinParentBrach());
		
		Iterator<SVNLogEntry> iteratorEntries = entries.iterator();
		
		while(iteratorEntries.hasNext()){
			SVNLogEntry revisionLog = iteratorEntries.next();
			if ( revisionLog.getChangedPaths( ).size( ) > 0 ) {
				Set<String> changedPathsSet = revisionLog.getChangedPaths( ).keySet( );
				
				 for ( Iterator changedPaths = changedPathsSet.iterator( ); changedPaths.hasNext( ); ) {
					 SVNLogEntryPath entryPath = ( SVNLogEntryPath ) revisionLog.getChangedPaths( ).get(changedPaths.next( ));
					 
					 RepositoryRevisionItem revisionItemLog = new RepositoryRevisionItem();
					 revisionItemLog.setRepositoryRevision(parse.parseToRepositoryRevision(revisionLog));
					 
					 if(isJavaFile(entryPath))
						 processRepositoryItem(entryPath, revisionItemLog);
					 
					 revisionItemLogs.add(revisionItemLog);
				 }
			}
		}
		
		return revisionItemLogs;
	}
	
	private void processRepositoryItem(SVNLogEntryPath entryPath, RepositoryRevisionItem revisionItemLog) { 
	   	 RepositoryItem item = parse.parseToRepositoryItem(entryPath);
	   	 revisionItemLog.setRepositoryItem(item);
	   	 
	   	 if(entryPath.getType() == 'A')
	   		 revisionItemLog.setCommitType(CommitType.ADD);
	   	 else if(entryPath.getType() == 'M')
	   		 revisionItemLog.setCommitType(CommitType.MOD);
	   	 else if(entryPath.getType() == 'D')
	   		 revisionItemLog.setCommitType(CommitType.DEL);
	   	 else if(entryPath.getType() == 'R')
	   		 revisionItemLog.setCommitType(CommitType.DEL);
	   		 
	}

	private boolean isJavaFile(SVNLogEntryPath entryPath){
		return entryPath.getKind() == SVNNodeKind.FILE 
	       		 && entryPath.getPath().contains(".java");
	}
	
	@Override
	public List<RepositoryRevision> getAllProjectRevision(Project project) {
		return listRevisions(project.getPath(), 1, -1);
	}
	
	@Override
	public List<RepositoryRevision> getRevisionsInRange(Project project, String start, String end) {
		Integer startRev = Integer.valueOf(start);
		Integer endRev = Integer.valueOf(end);
		return listRevisions(project.getPath(), startRev, endRev);
	}
	
	@Override
	public String getLastRevisionNumber(Project project){
		try {
			SVNDirEntry entry = repository.getDir(project.getPath(), -1, false, null);
			return String.valueOf(entry.getRevision());
		} catch (SVNException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private List<RepositoryRevision> listRevisions(String path, Integer startRevision, Integer endRevision) {
		List<RepositoryRevision> repositoryRevisions = new ArrayList<RepositoryRevision>();
		Collection<SVNLogEntry> revisions = getSVNLogEntryByRevision(path, startRevision, endRevision, false);
		
		Iterator<SVNLogEntry> iteratorRevisions = revisions.iterator();
		
		while(iteratorRevisions.hasNext()){
			SVNLogEntry revision = iteratorRevisions.next();
			RepositoryRevision repositoryRevision = parse.parseToRepositoryRevision(revision);
			repositoryRevisions.add(repositoryRevision);
		}
		
		return repositoryRevisions;
	}

	private Collection<SVNLogEntry> getSVNLogEntryByRevision(String path, Integer startRevision,
			Integer endRevision, boolean simpleBranchLog) {
		Collection<SVNLogEntry> revisions = new ArrayList<SVNLogEntry>();
		try {
			revisions = repository.log(new String[]{path}, null, startRevision, endRevision, true, simpleBranchLog);
			
			Iterator<SVNLogEntry> iteratorRevisions = revisions.iterator();
			while(iteratorRevisions.hasNext()){
				SVNLogEntry revision = iteratorRevisions.next();
				
				if(!revisions.contains(revision))
					revisions.add(revision);
			}
			
		} catch (SVNException e) {
			e.printStackTrace();
			System.err.println("Não foi possível recuperar as revisões no repositório");
		}
		
		return revisions;
	}

	@Override
	public String getFileContent(String path, String revision){
		try {
			return getFileContentInRepository(path, revision);
		} catch (RepositoryFileContentNotFoundException e) {
			return e.getMessage();
		}
	}

	private String getFileContentInRepository(String path, String revision)
			throws RepositoryFileContentNotFoundException {
		try {
			Long rev = Long.valueOf(revision);
;			SVNNodeKind nodeKind = repository.checkPath(path, rev);
			
			if(nodeKind != SVNNodeKind.FILE){
				throw new RepositoryFileContentNotFoundException(path, rev);
			}else{
				SVNProperties svnProperties = new SVNProperties();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				
				repository.getFile(path, rev, svnProperties, baos);
				
				return StringUtils.getFromCharBuffer(baos);
			}
			
		} catch (SVNException e) {
			e.printStackTrace();
		}
		return null;
	}	
	
	@Override
	public void closeConnection() {
		if(this.repository != null)
			this.repository.closeSession();
		
		this.repository = null;
	}

	@Override
	public Boolean isConnectionOpened() {
		return this.repository != null;
	}
	
	@Override
	public Boolean isGit() {
		return false;
	}

	@Override
	public Boolean isSVN() {
		return true;
	}

	@Override
	public Boolean isCVS() {
		return false;
	}

	@Override
	public String getNextRevisionToSync(Project project, String revisionLastSync) {
		if(revisionLastSync.equals(""))
			return "1";
		if(Long.valueOf(revisionLastSync) < Long.valueOf(getLastRevisionNumber(project)))
			return String.valueOf(Integer.parseInt(revisionLastSync) + 1);
		else
			return "0";
	}
	
}
