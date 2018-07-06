package unittesttemplate.template;

import org.eclipse.jdt.core.dom.ITypeBinding;

public class TypeAndName {

	private ITypeBinding type;

	private String name;

	private TypeAndName(ITypeBinding type, String name) {
		this.type = type;
		this.name = name;
	}

	public static TypeAndName of(ITypeBinding type) {
		return of(type, null);
	}

	public static TypeAndName of(ITypeBinding type, String name) {
		return new TypeAndName(type, name);
	}

	public ITypeBinding getType() {
		return this.type;
	}

	public String getName() {
		if (name != null && !"".equals(name)) {
			return name;
		}
		return Character.toLowerCase(this.type.getName().charAt(0))
				+ this.type.getName().substring(1);
	}
}
