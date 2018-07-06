package unittesttemplate.template;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;

import unittesttemplate.exceptions.NoClassException;
import unittesttemplate.exceptions.NoInterfaceException;

@SuppressWarnings("unchecked")
public class TemplateCreator extends TemplateBase {

	public CompilationUnit buildTestClass(CompilationUnit unit)
			throws NoClassException, NoInterfaceException {
		fromUnit = unit;
		initToUnit();
		initImports();
		fromClass = (TypeDeclaration) unit.types().stream().findFirst()
				.orElse(null);
		verify(fromClass);
		addPackageInfo();
		addMainClassImports(fromClass);
		this.buildSuperTypes();
		addClassDeclaration(fromClass);
		addFields(fromClass, toClass);
		addConfigurationClass(fromClass, toClass);
		addResetMethod();
		addMethods(fromClass, toClass);
		this.importList.forEach(imp -> this.addImport(imp, false));
		return toUnit;
	}

	private void addResetMethod() {
		MethodDeclaration resetMethod = ast.newMethodDeclaration();
		resetMethod.setName(ast.newSimpleName("resetMock"));

		resetMethod.modifiers().add(
				ast.newModifier(ModifierKeyword.PRIVATE_KEYWORD));
		resetMethod.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
		Block block = ast.newBlock();
		resetMethod.setBody(block);
		toClass.bodyDeclarations().add(resetMethod);
		if (this.toClass.getFields().length > 1) {
			MethodInvocation reset = ast.newMethodInvocation();
			reset.setExpression(ast.newSimpleName("Mockito"));
			reset.setName(ast.newSimpleName("reset"));
			for (int i = 1; i < this.toClass.getFields().length; ++i) {
				FieldDeclaration field = this.toClass.getFields()[i];
				String fieldName = this.getFieldNameFromField(field);
				reset.arguments().add(ast.newSimpleName(fieldName));
			}
			block.statements().add(ast.newExpressionStatement(reset));
		}
	}

	private void addMethods(TypeDeclaration fromClass, TypeDeclaration toClass) {
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
			if (isPublic && isAutowired) {
				addMethod(method);
			}
		}
	}

	private void addFields(TypeDeclaration fromClass, TypeDeclaration toClass) {
		String fromClassName = fromClass.getName().getIdentifier();
		String typeName = fromClassName
				.substring(0, fromClassName.length() - 4);
		String fieldName = this.firstCharToLowerCase(typeName);
		addField(typeName, fieldName);
		List<TypeAndName> fields = this.getFieldTypeAndNames();
		for (TypeAndName field : fields) {
			addField(field.getType().getName(), field.getName());
		}
	}

	private void addField(String typeName, String fieldName) {
		FieldDeclaration field = this.buildFieldDeclaration(typeName, fieldName);
		toClass.bodyDeclarations().add(field);
	}

	private void addConfigurationClass(TypeDeclaration fromClass,
			TypeDeclaration toClass) {
		TypeDeclaration configClass = ast.newTypeDeclaration();
		configClass.setName(ast.newSimpleName(toClass.getName().getIdentifier()
				+ "Configuration"));
		MarkerAnnotation annotation = ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newName("Configuration"));
		configClass.modifiers().add(annotation);
		configClass.modifiers().add(
				ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD));
		configClass.modifiers().add(
				ast.newModifier(ModifierKeyword.STATIC_KEYWORD));
		toClass.bodyDeclarations().add(configClass);

		String fromClassName = fromClass.getName().getIdentifier();
		String beanName = fromClassName
				.substring(0, fromClassName.length() - 4);
		ClassInstanceCreation expression = ast.newClassInstanceCreation();
		expression.setType(ast.newSimpleType(ast.newSimpleName(fromClassName)));
		configClass.bodyDeclarations().add(this.buildBeanMethod(beanName, false));

		for (ITypeBinding type : this.invokedServiceTypeBindings) {
			configClass.bodyDeclarations().add(this.buildBeanMethod(type.getName(), true));
		}
	}

	private void addClassDeclaration(TypeDeclaration type) {
		String name = type.getName().getIdentifier() + "Test";
		toClass = ast.newTypeDeclaration();
		toClass.setName(ast.newSimpleName(name));
		MarkerAnnotation context = ast.newMarkerAnnotation();
		context.setTypeName(ast.newSimpleName("ContextConfiguration"));
		this.importList
				.add("org.springframework.test.context.ContextConfiguration");
		toClass.modifiers().add(context);
		NormalAnnotation springRunner = ast.newNormalAnnotation();
		springRunner.setTypeName(ast.newSimpleName("RunWith"));
		this.importList.add("org.junit.runner.RunWith");
		TypeLiteral runnerType = ast.newTypeLiteral();
		runnerType.setType(ast.newSimpleType(ast
				.newSimpleName("SpringJUnit4ClassRunner")));
		this.importList
				.add("org.springframework.test.context.junit4.SpringJUnit4ClassRunner");
		MemberValuePair pair = ast.newMemberValuePair();
		pair.setValue(runnerType);
		pair.setName(ast.newSimpleName("value"));
		springRunner.values().add(pair);
		toClass.modifiers().add(springRunner);
		Modifier modifier = ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD);
		toClass.modifiers().add(modifier);
		toUnit.types().add(toClass);
	}

	private void addPackageInfo() {
		String newName = super.getToPackageName();
		PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
		packageDeclaration.setName(ast.newName(newName));
		toUnit.setPackage(packageDeclaration);
	}

	private void verify(TypeDeclaration type) throws NoClassException,
			NoInterfaceException {
		if (type == null) {
			throw new NoClassException();
		}
		if (type.superInterfaceTypes().isEmpty()) {
			throw new NoInterfaceException();
		}
	}

	private void addMainClassImports(TypeDeclaration type) {
		ITypeBinding typeBinding = type.resolveBinding();
		this.importList.add(typeBinding.getQualifiedName());
		this.importList.add(typeBinding.getInterfaces()[0].getQualifiedName());
	}

	private void initImports() {
		this.importList.addAll(Arrays.asList("org.junit.Assert",
				"org.junit.Ignore", "org.junit.Test", "org.mockito.Mockito",
				"org.springframework.beans.factory.annotation.Autowired",
				"org.springframework.context.annotation.Bean",
				"org.springframework.context.annotation.Configuration"));
	}

	private void initToUnit() {
		ast = fromUnit.getAST();
		toUnit = ast.newCompilationUnit();
	}
}
