/**
 * 
 */
package br.com.jvoliveira.sourceminer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import br.com.jvoliveira.sourceminer.domain.Project;
import br.com.jvoliveira.sourceminer.domain.RepositoryItem;

/**
 * @author Joao Victor
 *
 */
public interface RepositoryItemRepository extends CrudRepository<RepositoryItem, Long>{

	List<RepositoryItem> findByProject(Project project);
	
	List<RepositoryItem> findTop10ByProjectOrderByIdDesc(Project project);
	
	RepositoryItem findByPathAndName(String path, String name);
	
	@Query("SELECT count(ri) FROM RepositoryItem ri WHERE ri.project = ?1")
	Integer countTotalItensByProject(Project project);
}
