/**
 * 
 */
package br.com.jvoliveira.sourceminer.neo4j.domain;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import br.com.jvoliveira.sourceminer.domain.RepositoryItem;

/**
 * Nós do grafo que serão representações referentes à RepositoryItem
 * 
 * @author João Victor
 *
 */
@NodeEntity
public class ClassNode {

	@GraphId
	private Long id;
	
	private Long repositoryItemId;
	
	private String name;
	
	private String fullPath;
	
	/*
	 * Classes da qual a classe representada por esse nó dependem 
	 */
	@Relationship(type = "CALLS", direction = Relationship.OUTGOING)
	private List<ClassNode> dependencies = new ArrayList<>();
	
	@Relationship(type = "CALLS", direction = Relationship.INCOMING)
	private List<ClassNode> dependents = new ArrayList<>();
	
	public ClassNode(){
		
	}
	
	public ClassNode(Long repositoryItemId, String name){
		this.repositoryItemId = repositoryItemId;
		this.name = name;
	}
	
	public ClassNode(RepositoryItem item){
		this.repositoryItemId = item.getId();
		this.name = item.getName();
		this.fullPath = item.getFullPath();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getRepositoryItemId() {
		return repositoryItemId;
	}

	public void setRepositoryItemId(Long repositoryItemId) {
		this.repositoryItemId = repositoryItemId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getFullPath() {
		return fullPath;
	}

	public void setFullPath(String fullPath) {
		this.fullPath = fullPath;
	}

	public List<ClassNode> getDependencies() {
		return dependencies;
	}

	public void setDependencies(List<ClassNode> dependencies) {
		this.dependencies = dependencies;
	}

	public List<ClassNode> getDependents() {
		return dependents;
	}

	public void setDependents(List<ClassNode> dependents) {
		this.dependents = dependents;
	}
	
}
