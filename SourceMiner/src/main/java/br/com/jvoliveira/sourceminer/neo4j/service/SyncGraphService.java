/**
 * 
 */
package br.com.jvoliveira.sourceminer.neo4j.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
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
import br.com.jvoliveira.sourceminer.component.repositoryconnection.RepositoryRevisionItemHelper;
import br.com.jvoliveira.sourceminer.domain.ItemAsset;
import br.com.jvoliveira.sourceminer.domain.Project;
import br.com.jvoliveira.sourceminer.domain.RepositoryItem;
import br.com.jvoliveira.sourceminer.domain.RepositoryRevision;
import br.com.jvoliveira.sourceminer.domain.RepositorySyncLog;
import br.com.jvoliveira.sourceminer.domain.enums.AssetType;
import br.com.jvoliveira.sourceminer.neo4j.domain.ClassNode;
import br.com.jvoliveira.sourceminer.neo4j.domain.MethodCall;
import br.com.jvoliveira.sourceminer.neo4j.repository.ClassNodeRepository;
import br.com.jvoliveira.sourceminer.neo4j.repository.MethodCallRepository;
import br.com.jvoliveira.sourceminer.repository.ItemAssetRepository;
import br.com.jvoliveira.sourceminer.repository.RepositoryItemRepository;
import br.com.jvoliveira.sourceminer.repository.impl.SyncLogRepositoryImpl;
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
	private SyncLogRepositoryImpl syncLogRepository;
	
	@Autowired
	public SyncGraphService(RepositoryConnectionSession connection){
		this.connection = connection;
	}
	
	public void syncGraphUsingConfigurationObserver(Project project, SyncRepositoryObserver observer, RepositoryConnection connection){
		if(observer.isInSync())
			return;
		
		this.connection = new RepositoryConnectionSession();
		this.connection.setConnection(connection);
		
		System.out.println("Execute sync asynchronously - "
			      + Thread.currentThread().getName());
		
		this.observer = observer;
		this.observer.startSync();
		
		notifyObservers("Iniciando sincronização de grafo");
		synchronizeGraphUsingConfiguration(project);
		notifyObservers("Finalizando sincronização de grafo");
		
		this.observer.closeSync();
	}

	//TODO: Passo 3: Criar método como serviço para remover methodCalls de Assets desativados
	//TODO: Passo 4: Criar método como serviço para remover ClassNode e respectivos methodCalls para classes removidas.
	public void synchronizeGraphUsingConfiguration(Project project) {
		itemsForFirstSync(project);
		boolean firstSync = isFirstSync(project);
		if(firstSync && project.isGraphOutOfSync())
			refreshSyncLog(project);
		else if(!firstSync && project.isGraphOutOfSync()){
			Map<RepositoryItem,ClassNode> itemNode = getItensChangedAfterLastSync(project);
			createRelationshipInGraph(project,itemNode,true);
			refreshSyncLog(project);
		}
	}
	
	private Map<RepositoryItem, ClassNode> getItensChangedAfterLastSync(Project project) {
		List<String> twoLastRevisions = syncLogRepository.findTwoMostRecentRevisions(project);
		List<RepositoryRevision> revisions = connection.getConnection().getRevisionsInRange(project, twoLastRevisions.get(1), twoLastRevisions.get(0));
		List<String> revisionsHash = (List<String>) RepositoryRevisionItemHelper.getRevisions(revisions);
		
		List<RepositoryItem> itemsChangeInRevisions = itemRepository.findItemsChangedInRevisions(project, revisionsHash);
		return RepositoryRevisionItemHelper.loadRepositoryItemWithClassNode(itemsChangeInRevisions, nodeRepository);
	}

	private boolean isFirstSync(Project project){
		return syncLogRepository.countSyncLogByProject(project) <= 1;
	}
	
	private void itemsForFirstSync(Project project){
		List<RepositoryItem> itemWithoutNode = itemRepository.findItemWithoutNode(project);
		if(!itemWithoutNode.isEmpty()){
			Map<RepositoryItem,ClassNode> itemNode = createNodeForRepositoryItem(project,itemWithoutNode);
			createRelationshipInGraph(project, itemNode, false);
		}
	}

	private Map<RepositoryItem,ClassNode> createNodeForRepositoryItem(Project project, List<RepositoryItem> itemWithouNode) {
		Map<RepositoryItem,ClassNode> result = new HashMap<>();
		for(RepositoryItem item : itemWithouNode){
			ClassNode newClassNode = new ClassNode(item,project);
			nodeRepository.save(newClassNode);
			
			item.setGraphNodeId(newClassNode.getId());
			getDAO().update(item);
			result.put(item,newClassNode);
		}
		return result;
	}
	
	private void createRelationshipInGraph(Project project, Map<RepositoryItem,ClassNode> itemNode, boolean searchForMethodsCall) {
		Iterator<RepositoryItem> repositoryItems = itemNode.keySet().iterator();
		
		while(repositoryItems.hasNext()){
			RepositoryItem item = repositoryItems.next();
			ClassNode classNode = itemNode.get(item);
			
			Collection<MethodCall> methodsCall = new ArrayList<>();
			if(searchForMethodsCall){
				List<MethodCall> queryResult = nodeRepository.getAllMethodsCallFromNode(item.getId());
				if(queryResult != null)
					methodsCall.addAll(queryResult);
			}
			processCallGraph(classNode,item,methodsCall);
		}
		
	}
	
	private List<MethodCall> processCallGraph(ClassNode classNode, RepositoryItem item, Collection<MethodCall> methodsCall) {
		String headRevision = "-1";
		String fileContent = this.connection.getConnection().getFileContent(item.getFullPath(), headRevision);
		Map<String,Collection<String>> resultMap = CallGraphVisitorExecutor.processCallGraph(fileContent, item.getPath());
		return identifyRelationWithMethodCall(classNode,item,resultMap,methodsCall);
	}
	
	private List<MethodCall> identifyRelationWithMethodCall(ClassNode classNode, RepositoryItem item, Map<String,Collection<String>> resultMap,
			Collection<MethodCall> methodsCall) {
		List<MethodCall> result = new ArrayList<>();
		
		Iterator<String> iteratorResultMap = resultMap.keySet().iterator();
		while(iteratorResultMap.hasNext()){
			String className = iteratorResultMap.next();
			Collection<String> methodsCalledInClass = resultMap.get(className);
			for(String methodCalledInClass : methodsCalledInClass){
				List<MethodCall> methodsCalled = createAndPersistMethodCall(item.getProject(), className, methodCalledInClass,classNode,methodsCall);
				if(methodsCalled != null)
					result.addAll(methodsCalled);
			}
		}
		
		return result;
	}

	private List<MethodCall> createAndPersistMethodCall(Project project, String className, String methodCalledInClass, ClassNode classNode,
			Collection<MethodCall> methodsCall) {
		//TODO: Refatorar concatenação de extensão. Sugestão: Selecionar extensão de arquivos para sincronização nas configurações do projeto.
		RepositoryItem itemCalled = itemRepository.findFirstByName(className+".java");
		if(itemCalled == null)
			return null;
		
		List<MethodCall> methodsCalled = new ArrayList<>();
		List<ItemAsset> dependencies = itemAssetRepository.findByRepositoryItemAndEnableAndAssetType(itemCalled, true,AssetType.METHOD);
		for (ItemAsset asset : dependencies) {
			ClassNode nodeCalled = new ClassNode(itemCalled,project);
			if(!isExistsMethodCall(classNode,nodeCalled,asset,methodsCall) && methodCalledInClass.equals(asset.getName())){
				MethodCall relation = new MethodCall(classNode, nodeCalled);
				relation.setItemAssetId(asset.getId());
				relation.setMethodName(asset.getName());
				relation.setMethodSignature(asset.getSignature());
				methodCallRepository.save(relation);
				methodsCalled.add(relation);
			}
		}
		return methodsCalled;
	}

	private boolean isExistsMethodCall(ClassNode classNode, ClassNode nodeCalled, ItemAsset asset,
			Collection<MethodCall> methodsCall) {
		for(MethodCall method : methodsCall){
			if(method.getCalled().getId() == nodeCalled.getId()
					&& method.getCaller().getId() == classNode.getId()
					&& method.getItemAssetId() == asset.getId())
				return true;
		}
		return false;
	}
	
	private void refreshSyncLog(Project project) {
		RepositorySyncLog syncLog = project.getLastSync();
		syncLog.setGraphSyncDate(new Date());
		getDAO().update(syncLog);
	}

	
	public void notifyObservers(String mensagem){
		if(observer != null)
			observer.getNotification(mensagem);
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
	
	@Autowired
	public void setSyncLogRepository(SyncLogRepositoryImpl syncLogRepository){
		this.syncLogRepository = syncLogRepository;
	}
}
