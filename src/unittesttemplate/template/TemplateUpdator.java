package unittesttemplate.template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;

@SuppressWarnings("unchecked")
public class TemplateUpdator extends TemplateBase {

	private Set<String> existImportList = new HashSet<>();

	private Set<ITypeBinding> existFieldTypeBindings = new HashSet<>();

	private Set<String> existedMethodNames = new HashSet<>();

	public CompilationUnit updateTestClass(CompilationUnit unit)
			throws JavaModelException {
		fromUnit = unit;
		fromClass = (TypeDeclaration) unit.types().stream().findFirst()
				.orElse(null);
		toUnit = initToUnit();
		toClass = (TypeDeclaration) toUnit.types().stream().findFirst()
				.orElse(null);
		initExistImportList();
		initExistFieldTypeBindings();
		initExistedMethodNames();
		this.buildSuperTypes();
		this.addMoreFieldAndBeans();
		this.addMoreMethods();
		this.addMoreImports();
		return toUnit;
	}

	private void addMoreImports() {
		this.importList.stream()
				.filter(imp -> !this.existImportList.contains(imp))
				.forEach(imp -> this.addImport(imp, false));
	}

	private void addMoreMethods() {
		for (MethodDeclaration method : fromClass.getMethods()) {
			boolean isPublic = method
					.modifiers()
					.stream()
					.anyMatch(
							m -> (m instanceof Modifier)
									&& ((Modifier) m).isPublic());
			boolean isAutowired = method
					.modifiers()
					.stream()
					.anyMatch(
							m -> (m instanceof MarkerAnnotation)
									&& ((MarkerAnnotation) m).getTypeName()
											.getFullyQualifiedName()
											.equals("Override"));
			if (isPublic
					&& isAutowired
					&& !this.existedMethodNames.contains("test"
							+ this.firstCharToUpperCase(method.getName()
									.getIdentifier()))) {
				addMethod(method);
			}
		}
	}

	private void addMoreFieldAndBeans() {
		List<TypeAndName> fields = this.getFieldTypeAndNames();
		List<FieldDeclaration> toInsertFields = new ArrayList<>();
		List<MethodDeclaration> toInsertBeans = new ArrayList<>();
		List<String> additionalResetArguments = new ArrayList<>();
		for (TypeAndName field : fields) {
			if (!this
					.containsType(this.existFieldTypeBindings, field.getType())) {
				FieldDeclaration fieldDeclaration = this.buildFieldDeclaration(
						field.getType().getName(), field.getName());
				toInsertFields.add(fieldDeclaration);
				toInsertBeans.add(this.buildBeanMethod(field.getType()
						.getName(), true));
				additionalResetArguments.add(field.getName());
			}
		}
		this.insertMoreFields(toInsertFields);
		this.insertMoreBeans(toInsertBeans);
		this.addMoreArgumentsForReset(additionalResetArguments);
	}

	private void addMoreArgumentsForReset(List<String> additionalResetArguments) {
		if(additionalResetArguments.isEmpty()) {
			return;
		}
		for (MethodDeclaration method : toClass.getMethods()) {
			if ("resetMock".equals(method.getName().getIdentifier())) {
				try {
					ExpressionStatement statement = (ExpressionStatement) method
							.getBody().statements().get(0);
					MethodInvocation mi = (MethodInvocation) statement.getExpression();
					for(String arg : additionalResetArguments) {
						mi.arguments().add(ast.newSimpleName(arg));
					}
				} catch (Exception e) {
				}
			}
		}
	}

	private void insertMoreBeans(List<MethodDeclaration> toInsertBeans) {
		if (toInsertBeans.isEmpty()) {
			return;
		}
		if (toClass.getTypes().length == 0) {
			return;
		}
		for (MethodDeclaration method : toInsertBeans) {
			toClass.getTypes()[0].bodyDeclarations().add(method);
		}
	}

	private void insertMoreFields(List<FieldDeclaration> toInsertFields) {
		if (toInsertFields.isEmpty()) {
			return;
		}
		List<Object> copiedDeclarations = new ArrayList<>();
		for (Object obj : toClass.bodyDeclarations()) {
			copiedDeclarations.add(obj);
		}
		toClass.bodyDeclarations().clear();
		boolean inserted = false;
		for (int i = 0; i < copiedDeclarations.size(); ++i) {
			if ((copiedDeclarations.get(i) instanceof MethodDeclaration)
					|| (copiedDeclarations.get(i) instanceof TypeDeclaration)) {
				for (int j = 0; j < toInsertFields.size(); ++j) {
					toClass.bodyDeclarations().add(toInsertFields.get(j));
				}
				for (int j = i; j < copiedDeclarations.size(); ++j) {
					toClass.bodyDeclarations().add(copiedDeclarations.get(j));
				}
				inserted = true;
				break;
			} else {
				toClass.bodyDeclarations().add(copiedDeclarations.get(i));
			}
		}
		if (!inserted) {
			for (int j = 0; j < toInsertFields.size(); ++j) {
				toClass.bodyDeclarations().add(toInsertFields.get(j));
			}
		}
	}

	private void initExistedMethodNames() {
		for (MethodDeclaration method : toClass.getMethods()) {
			boolean isTest = method
					.modifiers()
					.stream()
					.anyMatch(
							m -> (m instanceof MarkerAnnotation)
									&& ((MarkerAnnotation) m).getTypeName()
											.getFullyQualifiedName()
											.equals("Test"));
			if (isTest) {
				this.existedMethodNames.add(method.getName().getIdentifier());
			}
		}
	}

	private void initExistFieldTypeBindings() {
		for (FieldDeclaration field : toClass.getFields()) {
			if ((field.getType().toString() + "Impl").equals(fromClass
					.getName().getIdentifier())) {
				continue;
			}
			if (this.isFieldAutowired(field)) {
				this.existFieldTypeBindings.add(field.getType()
						.resolveBinding());
				this.invokedServiceTypeBindings.add(field.getType()
						.resolveBinding());
			}
		}
	}

	private void initExistImportList() {
		for (Object obj : toUnit.imports()) {
			ImportDeclaration id = (ImportDeclaration) obj;
			String name = id.getName().getFullyQualifiedName();
			this.existImportList.add(name);
		}
	}

	private CompilationUnit initToUnit() throws JavaModelException {
		ASTParser astParser = ASTParser.newParser(AST.JLS8);
		ICompilationUnit x = fromUnit
				.getJavaElement()
				.getJavaProject()
				.findType(
						this.getToPackageName() + "."
								+ fromClass.getName().getIdentifier() + "Test")
				.getCompilationUnit();
		astParser.setSource(x.getWorkingCopy(null));
		astParser.setResolveBindings(true);
		astParser.setIgnoreMethodBodies(false);
		CompilationUnit cu = (CompilationUnit) (astParser.createAST(null));
		ast = cu.getAST();
		return cu;
	}
}
