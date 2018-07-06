package unittesttemplate.popup.actions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.PluginAction;
import org.eclipse.ui.part.FileEditorInput;

import unittesttemplate.template.TargetFilePathUtil;
import unittesttemplate.template.TemplateCreator;
import unittesttemplate.template.TemplateUpdator;

@SuppressWarnings("restriction")
public class CreateAction implements IObjectActionDelegate {

	@SuppressWarnings("unused")
	private Shell shell;

	private ASTParser astParser = ASTParser.newParser(AST.JLS8);

	/**
	 * Constructor for Action1.
	 */
	public CreateAction() {
		super();
	}

	/**
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		shell = targetPart.getSite().getShell();
	}

	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		if (action instanceof PluginAction) {
			PluginAction opAction = (PluginAction) action;
			ISelection selection = opAction.getSelection();
			if (selection instanceof TreeSelection) {
				try {
					TreeSelection treeSelection = (TreeSelection) selection;
					CompilationUnit unit = this.getCompilationUnit(treeSelection);
					String targetFileFullPath = TargetFilePathUtil.getTargetFileFullPath(treeSelection, unit);
					CompilationUnit toUnit = null;
					if(this.existedFile(targetFileFullPath)) {
						toUnit = new TemplateUpdator().updateTestClass(unit);
					} else {
						toUnit = new TemplateCreator().buildTestClass(unit);
					}
					this.createFile(toUnit.toString(), targetFileFullPath);
					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					String targetFilePath = TargetFilePathUtil.getTargetFileRelativePath(unit);
					IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromPortableString(
							this.getProjectName(treeSelection) + targetFilePath));
					file.refreshLocal(IResource.DEPTH_ZERO, null);
					IEditorDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry()
							.getDefaultEditor(file.getName());
					page.openEditor(new FileEditorInput(file), desc.getId());
				} catch (Exception e) {
					MessageDialog.openInformation(null, "Explore class In File System",
							Arrays.toString(e.getStackTrace()));
				}
			}
		}
		// MessageDialog.openInformation(
		// shell,
		// "Unit-test-template",
		// "Create was executed.");
	}

	private boolean existedFile(String path) {
		return new File(path).exists();
	}

	private String getProjectName(TreeSelection treeSelection) {
		return ((ICompilationUnit) treeSelection.getFirstElement()).getJavaProject().getPath().segments()[0];
	}

	/**
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
	}

	private void createFile(String testClassText, String targetFilePath) throws IOException {
		File file = new File(targetFilePath);
		if (!file.exists()) {
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			file.createNewFile();
		}
		FileOutputStream outSTr = new FileOutputStream(file);
		BufferedOutputStream buff = new BufferedOutputStream(outSTr);
		buff.write(testClassText.getBytes());
		buff.flush();
		buff.close();
	}

	public CompilationUnit getCompilationUnit(TreeSelection treeSelection) throws Exception {
		// String projectPath = this.getProjectPath(treeSelection);
		// String source = ((org.eclipse.jdt.internal.core.CompilationUnit)
		// treeSelection.getFirstElement()).getSource();
		// ICompilationUnit x = (ICompilationUnit) ((TreePath) treeSelection
		// .getPaths()[0]).getLastSegment();
		ICompilationUnit x = (ICompilationUnit) treeSelection.getFirstElement();
		// this.astParser.setSource(source.toCharArray());
		this.astParser.setSource(x);
		this.astParser.setResolveBindings(true);
		this.astParser.setIgnoreMethodBodies(false);
		// this.astParser.setEnvironment(null, new String[] { projectPath +
		// "/src/main/java" }, new String[] { "UTF-8" }, true);
		CompilationUnit result = (CompilationUnit) (this.astParser.createAST(null));
		return result;

	}
}
