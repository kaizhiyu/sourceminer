/**
 * 
 */
package br.com.jvoliveira.sourceminer.component.repositoryconnection;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.CoreMatchers.*;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import br.com.jvoliveira.sourceminer.domain.Project;
import br.com.jvoliveira.sourceminer.domain.ProjectConfiguration;
import br.com.jvoliveira.sourceminer.domain.RepositoryConnector;
import br.com.jvoliveira.sourceminer.domain.RepositoryItem;
import br.com.jvoliveira.sourceminer.domain.RepositoryLocation;
import br.com.jvoliveira.sourceminer.domain.RepositoryRevision;
import br.com.jvoliveira.sourceminer.domain.RepositoryRevisionItem;
import br.com.jvoliveira.sourceminer.domain.enums.RepositoryLocationType;
import br.com.jvoliveira.sourceminer.exceptions.RepositoryConnectionException;

/**
 * @author Joao Victor
 *
 */
public class RepositoryConnectionGITTest {

	private RepositoryConnectionGIT repoGit;
	private RepositoryConnector connector;
	
	@Before
	public void setup(){
		repoGit = new RepositoryConnectionGIT();
		connector = new RepositoryConnector();
		connector.setUsername("jvoliveiran@gmail.com");
		connector.setPassword("");
		connector.setName("Test-Sourcerminer");
		
		RepositoryLocation repoLocation = new RepositoryLocation();
		repoLocation.setUrl("https://github.com/jvoliveiran/sourceminer");
		repoLocation.setLocationType(RepositoryLocationType.REMOTE);
		
		connector.setRepositoryLocation(repoLocation);
		
		repoGit.setConnector(connector);
	}
	
	@After
	public void tearDown(){
		
	}
	
	@Test
	public void testConnectionGit(){
		try {
			repoGit.testConnection();
		} catch (RepositoryConnectionException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testGetAllProjectItens(){
		Project project = new Project();
		project.setPath("refs/heads/master");
		try {
			repoGit.openConnection();
			List<RepositoryItem> result = repoGit.getAllProjectItens(project);
			Assert.assertThat(result.size(), greaterThan(100));
			Assert.assertNotNull(result);
		} catch (RepositoryConnectionException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testGetRevisionItensInProjectRange(){
		ProjectConfiguration config = new ProjectConfiguration();
		config.setSyncStartRevision("69adbcc323f35ceafdfe256bdb9d065e53896a93");
		config.setSyncEndRevision("073dde90d2592fc9bc26a79ecef5b2b0fe4bb396");
		try {
			repoGit.openConnection();
			List<RepositoryRevisionItem> result = repoGit.getRevisionItensInProjectRange(null, config);
			Assert.assertNotNull(result);
			Assert.assertThat(result.size(), is(equalTo(3)));
		} catch (RepositoryConnectionException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testGetAllProjectRevision(){
		try {
			repoGit.openConnection();
			List<RepositoryRevision> result = repoGit.getAllProjectRevision(null);
			Assert.assertThat(result.size(), is(equalTo(119)));
		} catch (RepositoryConnectionException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testGetRevisionsInRange(){
		try {
			repoGit.openConnection();
			String from = "69adbcc323f35ceafdfe256bdb9d065e53896a93";
			String until = "073dde90d2592fc9bc26a79ecef5b2b0fe4bb396";
			List<RepositoryRevision> result = repoGit.getRevisionsInRange(null,from,until);
			Assert.assertThat(result.size(), is(equalTo(1)));
		} catch (RepositoryConnectionException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testGetLastRevisionNumber(){
		try {
			repoGit.openConnection();
			String result = repoGit.getLastRevisionNumber(null);
			Assert.assertThat(result, is(equalTo("6a4fc9196530eddd732ac1241bbd0e77fd3c3933")));
		} catch (RepositoryConnectionException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testGetFileContent(){
		String filePath = "SourceMiner/src/main/java/br/com/jvoliveira/sourceminer/component/repositoryconnection/RepositoryConnection.java";
		String revision = "6a4fc9196530eddd732ac1241bbd0e77fd3c3933";
		try{
			repoGit.openConnection();
			String result = repoGit.getFileContent(filePath, revision);
			Assert.assertNotNull(result);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
