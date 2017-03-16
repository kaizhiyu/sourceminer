/**
 * 
 */
package br.com.jvoliveira.sourceminer.neo4j.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import br.com.jvoliveira.arq.service.AbstractArqService;
import br.com.jvoliveira.sourceminer.component.javaparser.visitor.CallGraphVisitorExecutor;
import br.com.jvoliveira.sourceminer.component.repositoryconnection.RepositoryConnection;
import br.com.jvoliveira.sourceminer.component.repositoryconnection.RepositoryConnectionSession;
import br.com.jvoliveira.sourceminer.domain.ItemAsset;
import br.com.jvoliveira.sourceminer.domain.Project;
import br.com.jvoliveira.sourceminer.domain.RepositoryItem;
import br.com.jvoliveira.sourceminer.domain.enums.AssetType;
import br.com.jvoliveira.sourceminer.neo4j.domain.ClassNode;
import br.com.jvoliveira.sourceminer.neo4j.domain.MethodCall;
import br.com.jvoliveira.sourceminer.neo4j.repository.ClassNodeRepository;
import br.com.jvoliveira.sourceminer.neo4j.repository.MethodCallRepository;
import br.com.jvoliveira.sourceminer.repository.ItemAssetRepository;
import br.com.jvoliveira.sourceminer.repository.RepositoryItemRepository;
import br.com.jvoliveira.sourceminer.sync.SyncRepositoryObserver;

/**
 * @author João Victor
 *
 */
@Service
public class SyncGraphService extends AbstractArqService<Project>{

	private RepositoryConnectionSession connection;
	private SyncRepositoryObserver observer;
	
	private RepositoryItemRepository itemRepository;
	private ItemAssetRepository itemAssetRepository;
	private ClassNodeRepository nodeRepository;
	private MethodCallRepository methodCallRepository;
	
	public void syncGraphUsingConfigurationObserver(Project project, SyncRepositoryObserver observer, RepositoryConnection connection){
		if(observer.isInSync())
			return;
		
		this.connection = new RepositoryConnectionSession();
		this.connection.setConnection(connection);
		
		System.out.println("Execute sync asynchronously - "
			      + Thread.currentThread().getName());
		
		this.observer = observer;
		this.observer.startSync();
		
		synchronizeGraphUsingConfiguration(project);
		
		this.observer.closeSync();
	}

	public void synchronizeGraphUsingConfiguration(Project project) {
		//Passo 1: Criar novos nós
		List<RepositoryItem> itemWithoutNode = itemRepository.findItemWithoutNode(project);
		Map<RepositoryItem,ClassNode> itemNode = createNodeForRepositoryItem(itemWithoutNode);
		
		//TODO: Utilizar a mesma lista de items para sincronizar chamada de métodos
		createRelationshipInGraph(itemNode);
		
		//TODO: Passo 2: Criar methodCalls para novos assets em nós já existentes
		
		//TODO: Passo 3: Criar método como serviço para remover methodCalls de Assets desativados
		//TODO: Passo 4: Criar método como serviço para remover ClassNode e respectivos methodCalls para classes removidas.
		
	}

	private Map<RepositoryItem,ClassNode> createNodeForRepositoryItem(List<RepositoryItem> itemWithouNode) {
		Map<RepositoryItem,ClassNode> result = new HashMap<>();
		for(RepositoryItem item : itemWithouNode){
			ClassNode newClassNode = new ClassNode(item);
			nodeRepository.save(newClassNode);
			
			item.setGraphNodeId(newClassNode.getId());
			getDAO().update(item);
			result.put(item,newClassNode);
		}
		return result;
	}
	
	private void createRelationshipInGraph(Map<RepositoryItem,ClassNode> itemNode) {
		Iterator<RepositoryItem> repositoryItems = itemNode.keySet().iterator();
		
		while(repositoryItems.hasNext()){
			RepositoryItem item = repositoryItems.next();
			ClassNode classNode = itemNode.get(item);
//			List<MethodCall> relations = identifyRelationWithDependency(classNode, item);
			List<MethodCall> callGraph = processCallGraph(classNode,item);
		}
		
	}
	
	//TODO: Pendente teste de sincronização.
	private List<MethodCall> processCallGraph(ClassNode classNode, RepositoryItem item) {
		String headRevision = "-1";
		String fileContent = this.connection.getConnection().getFileContent(item.getFullPath(), headRevision);
		Map<String,Collection<String>> resultMap = CallGraphVisitorExecutor.processCallGraph(fileContent, item.getPath());
		return identifyRelationWithMethodCall(classNode,item,resultMap);
	}
	
	private List<MethodCall> identifyRelationWithMethodCall(ClassNode classNode, RepositoryItem item, Map<String,Collection<String>> resultMap) {
		List<MethodCall> result = new ArrayList<>();
		
		Iterator<String> iteratorResultMap = resultMap.keySet().iterator();
		while(iteratorResultMap.hasNext()){
			String className = iteratorResultMap.next();
			Collection<String> methodsCalledInClass = resultMap.get(className);
			for(String methodCalledInClass : methodsCalledInClass)
				result.addAll(createAndPersistMethodCall(className, methodCalledInClass,classNode));
		}
		
		return result;
	}

	private List<MethodCall> createAndPersistMethodCall(String className, String methodCalledInClass, ClassNode classNode) {
		RepositoryItem itemCalled = itemRepository.findByName(className);
		if(itemCalled == null)
			return null;
		
		List<MethodCall> methodsCalled = new ArrayList<>();
		//TODO: Verificar para criar apenas methodCall de novos assets. Opção incluir campo com número de sincronização nos Assets
		List<ItemAsset> dependencies = itemAssetRepository.findByRepositoryItemAndEnableAndAssetType(itemCalled, true,AssetType.METHOD);
		for (ItemAsset asset : dependencies) {
			MethodCall relation = new MethodCall(classNode, new ClassNode(itemCalled));
			relation.setItemAssetId(asset.getId());
			relation.setMethodName(asset.getName());
			relation.setMethodSignature(asset.getSignature());
			methodCallRepository.save(relation);
			methodsCalled.add(relation);
		}
		return methodsCalled;
	}

	/**
	 * Not used for now
	 * @param classNode
	 * @param item
	 * @return
	 */
	private List<MethodCall> identifyRelationWithDependency(ClassNode classNode, RepositoryItem item) {
		List<MethodCall> result = new ArrayList<>();
		List<ItemAsset> dependencies = itemAssetRepository.findByRepositoryItemAndEnableAndAssetType(item, true, AssetType.IMPORT);
		for(ItemAsset asset : dependencies){
			RepositoryItem importItem = asset.getImportRepositoryItem();
			if(importItem != null){
				MethodCall relation = new MethodCall(classNode, new ClassNode(importItem));
				methodCallRepository.save(relation);
				result.add(relation);
			}
		}
		return result;
	}

	@Autowired
	public void setRepositoryItemRepositoryImpl(RepositoryItemRepository repository){
		this.itemRepository = repository;
	}
	
	@Autowired
	public void setClassNodeRepository(ClassNodeRepository nodeRepository){
		this.nodeRepository = nodeRepository;
	}
	
	@Autowired
	public void setItemAssetRepository(ItemAssetRepository itemAssetRepository){
		this.itemAssetRepository = itemAssetRepository;
	}
	
	@Autowired
	public void setMethodCallRepository(MethodCallRepository methodCallRepository){
		this.methodCallRepository = methodCallRepository;
	}
}
