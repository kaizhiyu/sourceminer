/**
 * 
 */
package br.com.jvoliveira.sourceminer.component.repositoryconnection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;

import br.com.jvoliveira.sourceminer.domain.Project;
import br.com.jvoliveira.sourceminer.domain.ProjectConfiguration;
import br.com.jvoliveira.sourceminer.domain.RepositoryConnector;
import br.com.jvoliveira.sourceminer.domain.RepositoryItem;
import br.com.jvoliveira.sourceminer.domain.RepositoryRevision;
import br.com.jvoliveira.sourceminer.domain.RepositoryRevisionItem;
import br.com.jvoliveira.sourceminer.exceptions.RepositoryConnectionException;

/**
 * @author Joao Victor
 *
 */
public class RepositoryConnectionGIT implements RepositoryConnection{
	
	private RepositoryConnector connector;
	private Git gitSource;
	
	private RepositoryParseGIT parse = new RepositoryParseGIT();

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
		return parse;
	}

	@Override
	public void openConnection() throws RepositoryConnectionException {
		if(connector.getRepositoryLocation().getLocationType().isLocal())
			gitSource = openLocalConnection(connector.getRepositoryLocation().getUrl());
		
		else if(connector.getRepositoryLocation().getLocationType().isRemote())
			gitSource = openRemoteConnection();
		
	}

	private Git openRemoteConnection() {
		Git git = null;
		String username = connector.getUsername();
		String password = connector.getPassword();
		String sourcePath = connector.getRepositoryLocation().getUrl();
		String localPath = "/Users/MacBook/sourcermine_git_repo/"+connector.getName();
		
		if(isExistLocalRepository())
			return openLocalConnection(localPath);
		
		CredentialsProvider credential = new UsernamePasswordCredentialsProvider(username, password);
		try {
			git = Git.cloneRepository()
					  .setDirectory(new File(localPath))
					  .setURI(sourcePath)
					  .setCredentialsProvider(credential)
					  .call();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
		return git;
	}
	
	private boolean isExistLocalRepository(){
		String localPath = "/Users/MacBook/sourcermine_git_repo/"+connector.getName();
		
		File varTmpDir = new File(localPath);
		return varTmpDir.exists();
	}

	private Git openLocalConnection(String localPath) {
		Git git = null;
		try {
			git = Git.open(new File(localPath) );
		} catch (IOException e) {
			e.printStackTrace();
		}
		return git;
	}

	@Override
	public void closeConnection() {
		if(gitSource != null)
			gitSource.close();
	}

	@Override
	public boolean testConnection() throws RepositoryConnectionException {
		try{
			openConnection();
			showGitStatus();
		} catch (NoWorkTreeException e) {
			e.printStackTrace();
			return false;
		} catch (GitAPIException e) {
			e.printStackTrace();
			return false;
		}finally{
			if(isConnectionOpened())
				closeConnection();
		}
		return true;
	}

	private void showGitStatus() throws NoWorkTreeException, GitAPIException {
		 Status status = gitSource.status().call();
         System.out.println("Added: " + status.getAdded());
         System.out.println("Changed: " + status.getChanged());
         System.out.println("Conflicting: " + status.getConflicting());
         System.out.println("ConflictingStageState: " + status.getConflictingStageState());
         System.out.println("IgnoredNotInIndex: " + status.getIgnoredNotInIndex());
         System.out.println("Missing: " + status.getMissing());
         System.out.println("Modified: " + status.getModified());
         System.out.println("Removed: " + status.getRemoved());
         System.out.println("Untracked: " + status.getUntracked());
         System.out.println("UntrackedFolders: " + status.getUntrackedFolders());
	}

	@Override
	public Boolean isConnectionOpened() {
		return this.gitSource != null;
	}

	@Override
	public Boolean isGit() {
		return true;
	}

	@Override
	public Boolean isSVN() {
		return false;
	}

	@Override
	public Boolean isCVS() {
		return false;
	}

	@Override
	public List<RepositoryItem> getAllProjectItens(Project project) {
		List<RepositoryItem> resultItem = new ArrayList<>();
		Repository repository = gitSource.getRepository();
		Ref head;
		try {
			head = repository.getRef(project.getPath());
			  // a RevWalk allows to walk over commits based on some filtering that is defined
	        RevWalk walk = new RevWalk(repository);

	        RevCommit commit = walk.parseCommit(head.getObjectId());
	        RevTree tree = commit.getTree();
	        System.out.println("Having tree: " + tree);
	        
	        // now use a TreeWalk to iterate over all files in the Tree recursively
	        // you can set Filters to narrow down the results if needed
			TreeWalk treeWalk = new TreeWalk(repository);
			treeWalk.addTree(tree);
			treeWalk.setRecursive(false);
			while (treeWalk.next()) {
			    if (treeWalk.isSubtree())
			        treeWalk.enterSubtree();
			    else if(isJavaFile(treeWalk)){
			    	resultItem.add(parse.parseTreeWalkToRepositoryItem(treeWalk));
			        System.out.println("file: " + treeWalk.getPathString());
			    }
			}
	        
		} catch (IOException e) {
			e.printStackTrace();
		}
		return resultItem;
	}

	private boolean isJavaFile(TreeWalk treeWalk) {
		return treeWalk.getNameString().contains(".java");
	}
	
	@Override
	public List<RepositoryItem> getItensInRevision(Project project, Integer startRevision, Integer endRevision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<RepositoryRevisionItem> getRevisionItensInProjectRange(Project project, ProjectConfiguration config) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<RepositoryRevision> getAllProjectRevision(Project project) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<RepositoryRevision> getRevisionsInRange(Project project, Integer start, Integer end) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long getLastRevisionNumber(Project project) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getFileContent(String path, Long revision) {
		// TODO Auto-generated method stub
		return null;
	}

}
