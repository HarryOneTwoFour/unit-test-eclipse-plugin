package unittesttemplate.template;

import java.lang.reflect.Field;

import org.eclipse.core.internal.resources.Project;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;

@SuppressWarnings("restriction")
public class TargetFilePathUtil {

	private TargetFilePathUtil() {
		
	}
	public static String getTargetFileFullPath(TreeSelection treeSelection, CompilationUnit unit) throws Exception {
		return getProjectFilePath(treeSelection) + getTargetFileRelativePath(unit);
	}
	public static String getTargetFileRelativePath(CompilationUnit unit) {
		String result = "/src/test/java";
		String[] packageArray = getTargetPackageName(unit).split("\\.");
		for (int i = 0; i < packageArray.length; ++i) {
			result += "/" + packageArray[i];
		}
		result += "/" + ((TypeDeclaration) unit.types().get(0)).getName().getIdentifier() + "Test.java";
		return result;
	}
	
	public static String getProjectFilePath(TreeSelection treeSelection) throws Exception {
		TreePath[] treePaths = treeSelection.getPaths();
		Object javaProject = treePaths[0].getFirstSegment();
		Field field = javaProject.getClass().getDeclaredField("project");
		field.setAccessible(true);
		Project project = (Project) field.get(javaProject);
		String projectPath = project.getLocationURI().getPath();
		if ('/' == projectPath.charAt(0)) {
			projectPath = projectPath.substring(1);
		}
		return projectPath;
	}
	
	public static String getTargetPackageName(CompilationUnit unit) {
		String packageName = unit.getPackage().getName().toString();
		if (packageName.endsWith(".impl")) {
			packageName = packageName.substring(0, packageName.length() - 5);
		}
		String[] packageArray = packageName.split("\\.");
		String newName = "";
		if (packageArray.length >= 6) {// a.b.c.d.e.f.g.h.i ->
										// a.b.c.d.e.test.g.h.i
			for (int i = 0; i < 5; ++i) {
				newName += "." + packageArray[i];
			}
			newName += ".test";
			for (int i = 5; i < packageArray.length; ++i) {
				newName += "." + packageArray[i];
			}
		} else {// a.b.c.d -> a.b.c.test.d
			for (int i = 0; i < packageArray.length - 1; ++i) {
				newName += "." + packageArray[i];
			}
			newName += ".test";
			if (packageArray.length >= 1) {
				newName += "." + packageArray[packageArray.length - 1];
			}
		}
		// remove first "."
		newName = newName.substring(1);
		return newName;
	}
}
