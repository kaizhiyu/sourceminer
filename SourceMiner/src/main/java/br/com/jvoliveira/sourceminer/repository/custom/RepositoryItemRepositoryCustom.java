package br.com.jvoliveira.sourceminer.repository.custom;

import br.com.jvoliveira.sourceminer.domain.Project;
import br.com.jvoliveira.sourceminer.domain.RepositoryItem;

/**
 * @author Joao Victor
 *
 */
public interface RepositoryItemRepositoryCustom {

	public RepositoryItem findItemByFullPath(String importPath, Project project, String extension); 
	public Long findLastRevisionInFile(String filePath, Long idProject);
}
