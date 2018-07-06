package unittesttemplate.template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;

@SuppressWarnings("unchecked")
public abstract class TemplateBase {

	protected AST ast;
	protected CompilationUnit toUnit;
	protected CompilationUnit fromUnit;
	protected List<ITypeBinding> superTypes = new ArrayList<>();
	protected Set<String> importList = new HashSet<>();
	protected Set<ITypeBinding> invokedServiceTypeBindings = new HashSet<>();
	protected TypeDeclaration toClass;
	protected TypeDeclaration fromClass;
	
	protected final String getToPackageName() {
		return TargetFilePathUtil.getTargetPackageName(this.fromUnit);
	}

	protected final String getFieldNameFromType(ITypeBinding type) {
		for (FieldDeclaration field : this.toClass.getFields()) {
			if (type.getName().equals(field.getType().toString())) {
				return this.getFieldNameFromField(field);
			}
		}
		return null;
	}

	protected final String getFieldNameFromField(FieldDeclaration field) {
		return ((VariableDeclarationFragment) field.fragments().get(0))
				.getName().getIdentifier();
	}

	protected final boolean isFieldAutowired(FieldDeclaration field) {
		return field
				.modifiers()
				.stream()
				.anyMatch(
						x -> (x instanceof MarkerAnnotation)
								&& (((MarkerAnnotation) x).getTypeName()
										.getFullyQualifiedName()
										.equals("Autowired")));
	}

	protected final boolean isFieldAutowired(IVariableBinding field) {
		for (IAnnotationBinding annotation : field.getAnnotations()) {
			if ("org.springframework.beans.factory.annotation.Autowired"
					.equals(annotation.getAnnotationType().getQualifiedName())) {
				return true;
			}
		}
		return false;
	}

	protected final void buildSuperTypes() {
		ITypeBinding type = fromClass.resolveBinding();
		while (true) {
			ITypeBinding superType = type.getSuperclass();
			if (superType != null
					&& !"java.lang.Object".equals(superType.getQualifiedName())) {
				this.superTypes.add(superType);
			} else {
				break;
			}
			type = superType;
		}
	}

	protected final List<TypeAndName> getFieldTypeAndNames() {
		List<TypeAndName> result = new ArrayList<>();
		for (FieldDeclaration field : fromClass.getFields()) {
			if (isFieldAutowired(field)) {
				this.invokedServiceTypeBindings.add(field.getType()
						.resolveBinding());
				result.add(TypeAndName.of(field.getType().resolveBinding(),
						this.getFieldNameFromField(field)));
				this.importList.add(field.getType().resolveBinding()
						.getQualifiedName());
			}
		}
		for (ITypeBinding superType : this.superTypes) {
			for (IVariableBinding field : superType.getDeclaredFields()) {
				if (isFieldAutowired(field)
						&& !this.invokedServiceTypeBindings.contains(field
								.getType())) {
					this.invokedServiceTypeBindings.add(field.getType());
					result.add(TypeAndName.of(field.getType()));
					this.importList.add(field.getType().getQualifiedName());
				}
			}
		}
		return result;
	}

	protected final String firstCharToLowerCase(String name) {
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

	protected final String firstCharToUpperCase(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	protected final FieldDeclaration buildFieldDeclaration(String typeName,
			String fieldName) {
		VariableDeclarationFragment fragment = ast
				.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(fieldName));
		FieldDeclaration field = ast.newFieldDeclaration(fragment);
		field.setType(ast.newSimpleType(ast.newSimpleName(typeName)));
		MarkerAnnotation annotation = ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newName("Autowired"));
		field.modifiers().add(annotation);
		field.modifiers().add(ast.newModifier(ModifierKeyword.PRIVATE_KEYWORD));
		return field;
	}

	protected final boolean containsType(Set<ITypeBinding> types,
			ITypeBinding type) {
		for (ITypeBinding t : types) {
			if (t.isEqualTo(type)) {
				return true;
			}
		}
		return false;
	}

	protected final MethodDeclaration buildBeanMethod(String beanName,
			boolean isMock) {
		MethodDeclaration method = ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(firstCharToLowerCase(beanName)));
		MarkerAnnotation annotation = ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newName("Bean"));
		method.modifiers().add(annotation);
		method.modifiers().add(ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD));
		method.setReturnType2(ast.newSimpleType(ast.newSimpleName(beanName)));
		Block block = ast.newBlock();
		ReturnStatement returnStatement = ast.newReturnStatement();
		Expression returnExpression = null;
		if (isMock) {
			MethodInvocation mock = ast.newMethodInvocation();
			mock.setExpression(ast.newSimpleName("Mockito"));
			mock.setName(ast.newSimpleName("mock"));
			TypeLiteral argument = ast.newTypeLiteral();
			argument.setType(ast.newSimpleType(ast.newSimpleName(beanName)));
			mock.arguments().add(argument);
			returnExpression = mock;
		} else {
			ClassInstanceCreation creation = ast.newClassInstanceCreation();
			creation.setType(ast.newSimpleType(ast.newSimpleName(fromClass
					.getName().getIdentifier())));
			returnExpression = creation;
		}
		returnStatement.setExpression(returnExpression);
		block.statements().add(returnStatement);
		method.setBody(block);
		return method;
	}

	protected void addMethod(MethodDeclaration fromMethod) {
		MethodDeclaration toMethod = this.addMethodDeclaration(fromMethod);
		Block block = toMethod.getBody();
		Set<IMethodBinding> invokedServices = findInvokedOtherServices(fromMethod);
		IMethodBinding fromMethodBinding = fromMethod.resolveBinding();
		this.addMethodBody(block, fromMethod, fromMethodBinding,
				invokedServices);
	}

	protected final MethodDeclaration addMethodDeclaration(
			MethodDeclaration fromMethod) {
		MethodDeclaration toMethod = ast.newMethodDeclaration();
		toMethod.setName(ast.newSimpleName("test"
				+ this.firstCharToUpperCase(fromMethod.getName()
						.getIdentifier())));
		MarkerAnnotation annotation = ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newName("Ignore"));
		toMethod.modifiers().add(annotation);
		annotation = ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newName("Test"));
		toMethod.modifiers().add(annotation);
		toMethod.modifiers().add(
				ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD));
		toMethod.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
		Block block = ast.newBlock();
		toMethod.setBody(block);
		toClass.bodyDeclarations().add(toMethod);
		return toMethod;
	}

	protected final Set<IMethodBinding> findInvokedOtherServices(
			MethodDeclaration method) {
		Set<IMethodBinding> result = new HashSet<>();
		IMethodBinding baseMethodBinding = method.resolveBinding();
		method.getBody().accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (node instanceof MethodInvocation) {
					MethodInvocation methodInvocation = (MethodInvocation) node;
					IMethodBinding methodBinding = methodInvocation
							.resolveMethodBinding();
					if (methodBinding.isEqualTo(baseMethodBinding)) {
						return;
					}
					if (TemplateBase.this.invokedServiceTypeBindings
							.contains(methodBinding.getDeclaringClass())) {
						result.add(methodBinding);
						return;
					}
					for (IMethodBinding mb : TemplateBase.this.fromClass
							.resolveBinding().getDeclaredMethods()) {
						if (mb.isEqualTo(methodBinding)) {
							result.addAll(TemplateBase.this.findInvokedOtherServices(TemplateBase.this
									.findMethodDeclarationByBinding(mb)));
							return;
						}
					}
				}
			}
		});
		return result;
	}

	protected final MethodDeclaration findMethodDeclarationByBinding(
			IMethodBinding mb) {
		for (MethodDeclaration md : fromClass.getMethods()) {
			if (md.resolveBinding().isEqualTo(mb)) {
				return md;
			}
		}
		return null;
	}

	protected final void addMethodBody(Block block,
			MethodDeclaration fromMethod, IMethodBinding fromMethodBinding,
			Set<IMethodBinding> invokedServices) {
		this.addMethodBodyVariableDeclarations(block, fromMethod,
				fromMethodBinding);
		this.addVariableInitializations(block, fromMethod, fromMethodBinding);
		this.addMockitoStub(block, invokedServices);
		this.addInvokeSpy(block, fromMethod);
		this.addMockitoInvokeVerify(block, invokedServices);
		this.addNoMoreVerify(block, invokedServices);
		this.addAssertions(block, fromMethodBinding);
		this.addResetMock(block);
	}

	private void addResetMock(Block block) {
		MethodInvocation reset = ast.newMethodInvocation();
		reset.setName(ast.newSimpleName("resetMock"));
		block.statements().add(ast.newExpressionStatement(reset));
	}

	private void addNoMoreVerify(Block block,
			Set<IMethodBinding> invokedServices) {
		Set<ITypeBinding> usedTypes = new HashSet<>();
		for (IMethodBinding method : invokedServices) {
			usedTypes.add(method.getDeclaringClass());
		}
		for (ITypeBinding type : usedTypes) {
			MethodInvocation noMore = ast.newMethodInvocation();
			noMore.setName(ast.newSimpleName("verifyNoMoreInteractions"));
			noMore.arguments().add(
					ast.newSimpleName(this.getFieldNameFromType(type)));
			noMore.setExpression(ast.newSimpleName("Mockito"));
			block.statements().add(ast.newExpressionStatement(noMore));
		}
	}

	private void addAssertions(Block block, IMethodBinding fromMethodBinding) {
		if (!"void".equals(fromMethodBinding.getReturnType().getName())) {
			String name = "assertEquals";
			if (fromMethodBinding.getReturnType().isArray()) {
				name = "assertArrayEquals";
			}
			MethodInvocation methodInvocation = ast.newMethodInvocation();
			methodInvocation.setName(ast.newSimpleName(name));
			methodInvocation.setExpression(ast.newSimpleName("Assert"));
			methodInvocation.arguments().add(
					ast.newSimpleName("expectedReturnValue"));
			methodInvocation.arguments().add(
					ast.newSimpleName("actualReturnValue"));
			block.statements()
					.add(ast.newExpressionStatement(methodInvocation));
		}
	}

	protected final void addMockitoInvokeVerify(Block block,
			Set<IMethodBinding> invokedServices) {
		for (IMethodBinding method : invokedServices) {
			if ("void".equals(method.getReturnType().getName())) {
				MethodInvocation serviceMethod = ast.newMethodInvocation();
				serviceMethod.setName(ast.newSimpleName(method.getName()));
				for (ITypeBinding type : method.getParameterTypes()) {
					serviceMethod.arguments().add(
							this.getTypeDefaultValue(type,
									this.firstCharToLowerCase(type.getName())));
				}
				MethodInvocation verify = ast.newMethodInvocation();
				verify.setName(ast.newSimpleName("verify"));
				verify.arguments().add(
						ast.newSimpleName(this.getFieldNameFromType(method
								.getDeclaringClass())));
				verify.setExpression(ast.newSimpleName("Mockito"));
				serviceMethod.setExpression(verify);
				block.statements().add(
						ast.newExpressionStatement(serviceMethod));
			}
		}
	}

	protected final void addInvokeSpy(Block block, MethodDeclaration fromMethod) {
		MethodInvocation methodInvocation = ast.newMethodInvocation();
		methodInvocation.setName(ast.newSimpleName(fromMethod.getName()
				.getIdentifier()));
		String toClassName = toClass.getName().getIdentifier();
		methodInvocation.setExpression(ast.newSimpleName(this
				.firstCharToLowerCase(toClassName.substring(0,
						toClassName.length() - 8))));
		for (Object obj : fromMethod.parameters()) {
			SingleVariableDeclaration svd = (SingleVariableDeclaration) obj;
			methodInvocation.arguments().add(
					ast.newSimpleName(svd.getName().getIdentifier()));
		}
		if (!"void".equals(fromMethod.getReturnType2().toString())) {
			VariableDeclarationFragment fragment = ast
					.newVariableDeclarationFragment();
			fragment.setName(ast.newSimpleName("actualReturnValue"));
			fragment.setInitializer(methodInvocation);
			VariableDeclarationStatement statement = ast
					.newVariableDeclarationStatement(fragment);
			statement.setType(this.typeFromBinding(fromMethod.getReturnType2()
					.resolveBinding()));
			block.statements().add(statement);
		} else {
			block.statements()
					.add(ast.newExpressionStatement(methodInvocation));
		}
	}

	protected final void addMockitoStub(Block block,
			Set<IMethodBinding> invokedServices) {
		for (IMethodBinding invocation : invokedServices) {
			if (!"void".equals(invocation.getReturnType().getName())) {
				MethodInvocation thenReturn = ast.newMethodInvocation();
				thenReturn.setName(ast.newSimpleName("thenReturn"));
				thenReturn.arguments().add(
						this.getTypeDefaultValue(invocation.getReturnType(),
								null));
				MethodInvocation serviceMethod = ast.newMethodInvocation();
				serviceMethod.setName(ast.newSimpleName(invocation.getName()));
				serviceMethod.setExpression(ast.newSimpleName(this
						.getFieldNameFromType(invocation.getDeclaringClass())));
				for (ITypeBinding parameterType : invocation
						.getParameterTypes()) {
					serviceMethod.arguments().add(
							this.getTypeDefaultValue(parameterType, null));
				}
				MethodInvocation when = ast.newMethodInvocation();
				when.setName(ast.newSimpleName("when"));
				when.arguments().add(serviceMethod);
				when.setExpression(ast.newSimpleName("Mockito"));
				thenReturn.setExpression(when);
				block.statements().add(ast.newExpressionStatement(thenReturn));
			}
		}
	}

	protected final void addVariableInitializations(Block block,
			MethodDeclaration fromMethod, IMethodBinding fromMethodBinding) {
		ITypeBinding returnTypeBinding = fromMethodBinding.getReturnType();
		if (!"void".equals(returnTypeBinding.getName())) {
			block.statements().add(
					this.buildAssignmentStatement(returnTypeBinding,
							"expectedReturnValue"));
		}
		for (int i = 0; i < fromMethod.parameters().size(); ++i) {
			String variableName = ((SingleVariableDeclaration) fromMethod
					.parameters().get(i)).getName().getIdentifier();
			ITypeBinding type = fromMethodBinding.getParameterTypes()[i];
			block.statements().add(
					this.buildAssignmentStatement(type, variableName));
		}
	}

	protected final void addMethodBodyVariableDeclarations(Block block,
			MethodDeclaration fromMethod, IMethodBinding fromMethodBinding) {
		ITypeBinding returnTypeBinding = fromMethodBinding.getReturnType();
		if (!"void".equals(returnTypeBinding.getName())) {
			block.statements().add(
					this.buildVariableDeclarationStatement(returnTypeBinding,
							"expectedReturnValue"));
		}
		for (int i = 0; i < fromMethod.parameters().size(); ++i) {
			String variableName = ((SingleVariableDeclaration) fromMethod
					.parameters().get(i)).getName().getIdentifier();
			ITypeBinding type = fromMethodBinding.getParameterTypes()[i];
			block.statements().add(
					this.buildVariableDeclarationStatement(type, variableName));
		}
	}

	protected final Expression getTypeDefaultValue(ITypeBinding type,
			String variableName) {
		if (type.isPrimitive()) {
			if ("boolean".equals(type.getName())) {
				return ast.newBooleanLiteral(false);
			}
			return ast.newNumberLiteral("0");
		}
		if (type.isParameterizedType()) {
			ITypeBinding erasure = type.getErasure();
			if ("java.util.List".equals(erasure.getQualifiedName())
					|| "java.util.Set".equals(erasure.getQualifiedName())
					|| "java.util.Map".equals(erasure.getQualifiedName())) {
				String variable = this.firstCharToLowerCase(type
						.getTypeArguments()[0].getName());
				Expression elementExpression = this.getTypeDefaultValue(
						type.getTypeArguments()[0], variable);
				MethodInvocation methodInvocation = ast.newMethodInvocation();
				methodInvocation.setName(ast.newSimpleName("asList"));
				methodInvocation.setExpression(ast.newSimpleName("Arrays"));
				methodInvocation.arguments().add(elementExpression);
				this.importList.add("java.util.Arrays");
				if ("java.util.List".equals(erasure.getQualifiedName())) {
					return methodInvocation;
				}
				if ("java.util.Set".equals(erasure.getQualifiedName())) {
					ClassInstanceCreation instanceCreation = ast
							.newClassInstanceCreation();
					instanceCreation.setType(ast.newParameterizedType(ast
							.newSimpleType(ast.newSimpleName("HashSet"))));
					instanceCreation.arguments().add(methodInvocation);
					this.importList.add("java.util.HashSet");
					return instanceCreation;
				}
				if("java.util.Map".equals(erasure.getQualifiedName())) {
					MethodInvocation put =ast.newMethodInvocation();
					put.setName(ast.newSimpleName("put"));
					put.arguments().add(this.getTypeDefaultValue(type.getTypeArguments()[0], null));
					put.arguments().add(this.getTypeDefaultValue(type.getTypeArguments()[1], null));
					Block block = ast.newBlock();
					block.statements().add(ast.newExpressionStatement(put));
					Initializer initializer = ast.newInitializer();
					initializer.setBody(block);
					AnonymousClassDeclaration anony = ast.newAnonymousClassDeclaration();
					anony.bodyDeclarations().add(initializer);
					ClassInstanceCreation instanceCreation = ast
							.newClassInstanceCreation();
					instanceCreation.setAnonymousClassDeclaration(anony);
					ParameterizedType newType = ast.newParameterizedType(ast.newSimpleType(ast.newSimpleName("HashMap")));
					newType.typeArguments().add(this.typeFromBinding(type.getTypeArguments()[0]));
					newType.typeArguments().add(this.typeFromBinding(type.getTypeArguments()[1]));
					instanceCreation.setType(newType);
					this.importList.add("java.util.HashMap");
					return instanceCreation;
				}
			}
			return ast.newNullLiteral();
		}
		if (type.isWildcardType() || type.isArray()) {
			return ast.newNullLiteral();
		}

		if ("java.lang.String".equals(type.getQualifiedName())) {
			StringLiteral stringLiteral = ast.newStringLiteral();
			stringLiteral.setLiteralValue(Optional.ofNullable(variableName)
					.orElse(this.firstCharToLowerCase(type.getName())));
			return stringLiteral;
		} else {
			this.importList.add(type.getQualifiedName());
			boolean hasBuilder = false;
			for (IAnnotationBinding annotation : type.getAnnotations()) {
				String name = annotation.getAnnotationType().getQualifiedName();
				;
				if ("lombok.Builder".equals(name)
						|| "lombok.experimental.Builder".equals(name)) {
					hasBuilder = true;
					break;
				}
			}
			if (hasBuilder) {
				return this.buildBuilderExpression(type);
			}
		}
		return ast.newNullLiteral();
	}

	protected final Expression buildBuilderExpression(ITypeBinding type) {
		MethodInvocation methodInvocation = ast.newMethodInvocation();
		methodInvocation.setExpression(ast.newSimpleName(type.getName()));
		methodInvocation.setName(ast.newSimpleName("builder"));
		MethodInvocation previousMethodInvocation = methodInvocation;
		for (IVariableBinding variable : type.getDeclaredFields()) {
			if ((ModifierKeyword.STATIC_KEYWORD.toFlagValue() & variable
					.getModifiers()) > 0) {
				continue;
			}
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setExpression(previousMethodInvocation);
			mi.setName(ast.newSimpleName(variable.getName()));
			mi.arguments().add(
					this.getTypeDefaultValue(variable.getType(),
							variable.getName()));
			previousMethodInvocation = mi;
		}
		MethodInvocation mi = ast.newMethodInvocation();
		mi.setExpression(previousMethodInvocation);
		mi.setName(ast.newSimpleName("build"));
		return mi;
	}

	protected final Type typeFromBinding(ITypeBinding typeBinding) {
		if (typeBinding.isPrimitive()) {
			return ast.newPrimitiveType(PrimitiveType.toCode(typeBinding
					.getName()));
		}
		if (typeBinding.isWildcardType()) {
			WildcardType capType = ast.newWildcardType();
			ITypeBinding bound = typeBinding.getBound();
			if (bound != null) {
				capType.setBound(typeFromBinding(bound),
						typeBinding.isUpperbound());
			}
			return capType;
		}
		if (typeBinding.isArray()) {
			Type elType = typeFromBinding(typeBinding.getElementType());
			return ast.newArrayType(elType, typeBinding.getDimensions());
		}
		if (typeBinding.isParameterizedType()) {
			ParameterizedType type = ast
					.newParameterizedType(typeFromBinding(typeBinding
							.getErasure()));
			for (ITypeBinding typeArg : typeBinding.getTypeArguments()) {
				type.typeArguments().add(typeFromBinding(typeArg));
			}
			return type;
		}
		return ast.newSimpleType(ast.newName(typeBinding.getName()));
	}

	protected final ExpressionStatement buildAssignmentStatement(
			ITypeBinding type, String variableName) {
		Assignment assignment = ast.newAssignment();
		assignment.setLeftHandSide(ast.newSimpleName(variableName));
		assignment.setRightHandSide(this
				.getTypeDefaultValue(type, variableName));
		return ast.newExpressionStatement(assignment);
	}

	protected final VariableDeclarationStatement buildVariableDeclarationStatement(
			ITypeBinding type, String variableName) {
		VariableDeclarationFragment fragment = ast
				.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(variableName));
		VariableDeclarationStatement expression = ast
				.newVariableDeclarationStatement(fragment);
		expression.setType(this.typeFromBinding(type));
		this.importList.addAll(this.getImportsFromTypeBinding(type));
		return expression;
	}

	protected final List<String> getImportsFromTypeBinding(
			ITypeBinding typeBinding) {
		List<String> imps = new ArrayList<>();
		if (typeBinding.isWildcardType()) {
			ITypeBinding bound = typeBinding.getBound();
			if (bound != null) {
				imps.addAll(this.getImportsFromTypeBinding(bound));
			}
		} else if (typeBinding.isArray()) {
			imps.addAll(this.getImportsFromTypeBinding(typeBinding
					.getElementType()));
		} else if (typeBinding.isParameterizedType()) {
			imps.addAll(this.getImportsFromTypeBinding(typeBinding.getErasure()));
			for (ITypeBinding typeArg : typeBinding.getTypeArguments()) {
				imps.addAll(this.getImportsFromTypeBinding(typeArg));
			}
		} else if (typeBinding.isPrimitive()) {
		} else {
			String name = typeBinding.getQualifiedName();
			if (!name.startsWith("java.lang")) {
				imps.add(name);
			}
		}
		return imps;
	}

	protected final void addImport(String imp, boolean isStatic) {
		if (imp == null) {
			return;
		}
		ImportDeclaration importDeclaration = ast.newImportDeclaration();
		importDeclaration.setName(ast.newName(imp));
		importDeclaration.setStatic(isStatic);
		toUnit.imports().add(importDeclaration);
	}
}
