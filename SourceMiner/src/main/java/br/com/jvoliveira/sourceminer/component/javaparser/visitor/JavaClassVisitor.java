/**
 * 
 */
package br.com.jvoliveira.sourceminer.component.javaparser.visitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * @author Joao Victor
 *
 */
public class JavaClassVisitor extends VoidVisitorAdapter<Object> implements GenericClassVisitor {

	private Map<String, FieldDeclaration> fields;
	private Map<String, MethodDeclaration> methods;
	private List<ImportDeclaration> imports;

	boolean ready;

	public JavaClassVisitor() {
		init();
	}

	@Override
	public void init() {
		this.fields = new HashMap<>();
		this.methods = new HashMap<>();
		this.imports = new ArrayList<>();

		ready = false;
	}
	
	@Override
	public void execute(CompilationUnit compUnit, Object arg, Map<String,Object> resultMap) {
		this.visit(compUnit, arg);
	}

	@Override
	public void visit(CompilationUnit compUnit, Object arg) {
		super.visit(compUnit, arg);
		ready = true;
	}

	@Override
	public void visit(MethodDeclaration methodDeclaration, Object arg) {
		methods.put(methodDeclaration.getName(), methodDeclaration);
	}

	@Override
	public void visit(ImportDeclaration importDeclaration, Object arg) {
		imports.add(importDeclaration);
	}

	@Override
	public void visit(FieldDeclaration fieldDeclaration, Object arg) {
		for (VariableDeclarator variableDeclaration : fieldDeclaration.getVariables()) {
			fields.put(variableDeclaration.getId().getName(), fieldDeclaration);
		}
	}

	public Map<String, FieldDeclaration> getFields() {
		return fields;
	}

	public Map<String, MethodDeclaration> getMethods() {
		return methods;
	}

	public List<ImportDeclaration> getImports() {
		return imports;
	}

	@Override
	public boolean isReady() {
		return ready;
	}
	
}
