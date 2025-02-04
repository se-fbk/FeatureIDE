/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2019  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 *
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.fm.ui.views.outline.custom;

import static de.ovgu.featureide.fm.core.localization.StringTable.UPDATE_OUTLINE_VIEW;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;

import de.ovgu.featureide.fm.ui.FMUIPlugin;
import de.ovgu.featureide.fm.ui.editors.FeatureModelEditor;
import de.ovgu.featureide.fm.ui.editors.configuration.ConfigurationEditor;
import de.ovgu.featureide.fm.ui.views.outline.custom.action.ChangeOutlineProviderAction;
import de.ovgu.featureide.fm.ui.views.outline.custom.action.CollapseAllAction;
import de.ovgu.featureide.fm.ui.views.outline.custom.action.ExpandAllAction;
import de.ovgu.featureide.fm.ui.views.outline.custom.action.FilterOutlineAction;
import de.ovgu.featureide.fm.ui.views.outline.custom.action.RemoveAllFiltersAction;
import de.ovgu.featureide.fm.ui.views.outline.custom.filters.IOutlineFilter;
import de.ovgu.featureide.fm.ui.views.outline.custom.providers.NotAvailableOutlineProvider;

/**
 * Another outline view displaying the same information as the collaboration diagram
 *
 * @author Jan Wedding
 * @author Melanie Pflaume
 * @author Reimar Schroeter
 * @author Dominic Labsch
 * @author Daniel Psche
 * @author Christopher Sontag
 * @author Kevin Jedelhauser
 * @author Johannes Herschel
 */
public class Outline extends ViewPart implements ISelectionChangedListener, ITreeViewerListener, IPropertyListener, IPageChangedListener {

	private static final String OUTLINE_ID = "de.ovgu.featureide.fm.ui.Outline";
	public static final String ID = "de.ovgu.featureide.ui.views.collaboration.outline.CollaborationOutline";
	private static final String CONTEXT_MENU_ID = "de.ovgu.feautureide.fm.ui.view.outline.contextmenu";

	private TreeViewer viewer;
	private IFile curFile;
	private UIJob updateOutlineJob;
	private IEditorPart part;

	private final List<OutlineProvider> providers = new ArrayList<>();
	private final OutlineProvider defaultProvider = new NotAvailableOutlineProvider();
	private OutlineProvider provider = defaultProvider;

	/**
	 * Input for the tree viewer when no file is opened, used instead of null so the tree viewer is not empty.
	 */
	private final Object notAvailableInput = new Object();

	private final IPartListener editorListener = new IPartListener() {

		@Override
		public void partOpened(IWorkbenchPart part) {
			// Not needed
		}

		@Override
		public void partDeactivated(IWorkbenchPart part) {
			// Not needed
		}

		@Override
		public void partClosed(IWorkbenchPart part) {
			if (part instanceof ConfigurationEditor) {
				final ConfigurationEditor editor = (ConfigurationEditor) part;
				editor.removePageChangedListener(Outline.this);
			} else if (part instanceof FeatureModelEditor) {
				final FeatureModelEditor editor = (FeatureModelEditor) part;
				editor.removePageChangedListener(Outline.this);
			}
			if (part instanceof IEditorPart) {
				setEditorActions(part.getSite().getPage().getActiveEditor());
			}
		}

		@Override
		public void partBroughtToTop(IWorkbenchPart part) {
			// Not needed
		}

		@Override
		public void partActivated(IWorkbenchPart part) {
			if (part instanceof ConfigurationEditor) {
				final ConfigurationEditor editor = (ConfigurationEditor) part;
				editor.addPageChangedListener(Outline.this);
			} else if (part instanceof FeatureModelEditor) {
				final FeatureModelEditor editor = (FeatureModelEditor) part;
				editor.addPageChangedListener(Outline.this);
			}
			if (part instanceof IEditorPart) {
				setEditorActions((IEditorPart) part);
			}
		}
	};

	private void checkForExtensions() {
		final IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(OUTLINE_ID);

		for (final IConfigurationElement e : config) {
			try {
				final Object outlineProvider = e.createExecutableExtension("OutlineProvider");
				if (outlineProvider instanceof OutlineProvider) {
					final OutlineProvider provider = (OutlineProvider) outlineProvider;
					provider.getLabelProvider().initTreeViewer(viewer);
					providers.add(provider);
				}
			} catch (final CoreException ex) {
				FMUIPlugin.getDefault().logError(ex);
			}
		}
	}

	public Outline() {
		super();
	}

	/**
	 * Updates the outline based on the active editor. Stores the active editor and file and selects an appropriate provider and updates it.
	 *
	 * @param activeEditor The active editor, null if there is no active editor
	 */
	private void setEditorActions(IEditorPart activeEditor) {
		OutlineProvider newProvider = null;
		IFile file = null;
		part = activeEditor;

		if (part != null) {
			final IEditorInput editorInput = part.getEditorInput();
			if (editorInput instanceof FileEditorInput) {
				// case: open editor
				final FileEditorInput inputFile = (FileEditorInput) editorInput;
				file = inputFile.getFile();
				part.addPropertyListener(this);

				final Control control = viewer.getControl();
				if ((control != null) && !control.isDisposed()) {
					if (file != null) {
						// Check whether we must change the actual provider
						if (!provider.isSupported(part, file) || (provider == defaultProvider)) {
							// Get the first provider that supports the resource
							for (final OutlineProvider p : providers) {
								if (p.isSupported(part, file)) {
									newProvider = p;
									break;
								}
							}
						} else {
							newProvider = provider;
						}
					}
				}
			}
		}

		if ((file != curFile) || (provider != newProvider)) {
			// Fallback when no provider is found -> NotAvailable
			if (newProvider == null) {
				newProvider = defaultProvider;
			}
			// Set actual provider and file and update the outline
			curFile = file;
			setProvider(newProvider);
		}
	}

	/**
	 * Sets and updates the provider.
	 *
	 * @param newProvider The new provider
	 */
	private void setProvider(OutlineProvider newProvider) {
		provider = newProvider;
		provider.setActiveEditor(part);
		update(curFile);
	}

	@Override
	public void createPartControl(Composite parent) {
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.getControl().setEnabled(false);
		viewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS);
		viewer.addSelectionChangedListener(this);
		viewer.addTreeListener(this);

		checkForExtensions();

		final IWorkbenchPage page = getSite().getPage();
		page.addPartListener(editorListener);

		final IEditorPart activeEditor = page.getActiveEditor();
		if (activeEditor != null) {
			setEditorActions(activeEditor);
		} else {
			setProvider(defaultProvider);
		}

		updateActions();
	}

	/**
	 * Update all actions depending on the current provider.
	 */
	private void updateActions() {
		fillLocalToolBar();
		fillGlobalActions();
		fillContextMenu();
	}

	private void fillLocalToolBar() {
		final IToolBarManager manager = getViewSite().getActionBars().getToolBarManager();

		manager.removeAll();
		provider.initToolbarActions(manager);

		final CollapseAllAction collapseAllAction = new CollapseAllAction(viewer);
		collapseAllAction.addPropertyChangeListener(new IPropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent event) {
				handleCollapseAll(event);
			}
		});
		final ExpandAllAction expandAllAction = new ExpandAllAction(viewer);
		expandAllAction.addPropertyChangeListener(new IPropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent event) {
				handleExpandAll(event);
			}
		});

		manager.add(collapseAllAction);
		manager.add(expandAllAction);

		if (provider.getFilters() != null) {
			final IAction filterSelection = new Action("", SWT.DROP_DOWN) {};
			filterSelection.setImageDescriptor(FMUIPlugin.getDefault().getImageDescriptor("icons/filter_history.gif"));
			filterSelection.setMenuCreator(new IMenuCreator() {

				Menu fMenu = null;

				@Override
				public Menu getMenu(Menu parent) {
					return parent;
				}

				@Override
				public Menu getMenu(Control parent) {
					fMenu = new Menu(parent);
					if (curFile != null) {
						for (final IOutlineFilter filter : provider.getFilters()) {
							final IAction filterSelectionSpecific = new FilterOutlineAction(filter) {

								@Override
								public void run() {
									final OutlineTreeContentProvider treeProvider = provider.getTreeProvider();
									if (!treeProvider.hasFilter(getFilter())) {
										treeProvider.addFilter(getFilter());
									} else {
										treeProvider.removeFilter(getFilter());
									}
									update(curFile);
								}

							};

							final ActionContributionItem item = new ActionContributionItem(filterSelectionSpecific);
							item.fill(fMenu, -1);
						}
						final Separator sep = new Separator(IWorkbenchActionConstants.MB_ADDITIONS);
						sep.fill(fMenu, -1);
						final IAction providerSelectionSpecific = new RemoveAllFiltersAction(provider) {

							@Override
							public void run() {
								provider.getTreeProvider().removeAllFilters();
								update(curFile);
							}

						};

						final ActionContributionItem item = new ActionContributionItem(providerSelectionSpecific);
						item.fill(fMenu, -1);
					}
					return fMenu;
				}

				@Override
				public void dispose() {
					if (fMenu != null) {
						fMenu.dispose();
					}
				}

			});
			manager.add(filterSelection);
		}

		final IAction providerSelection = new Action("", SWT.DROP_DOWN) {};
		providerSelection.setImageDescriptor(FMUIPlugin.getDefault().getImageDescriptor("icons/monitor_obj.gif"));
		providerSelection.setMenuCreator(new IMenuCreator() {

			Menu fMenu = null;

			@Override
			public Menu getMenu(Menu parent) {
				return parent;
			}

			@Override
			public Menu getMenu(Control parent) {
				fMenu = new Menu(parent);

				if (curFile != null) {
					for (final OutlineProvider p : providers) {
						if (p.isSupported(part, curFile) && !p.getProviderName().isEmpty()) {
							final IAction providerSelectionSpecific = new ChangeOutlineProviderAction(p, provider == p) {

								@Override
								public void run() {
									setProvider(getProvider());
								}

							};

							final ActionContributionItem item = new ActionContributionItem(providerSelectionSpecific);
							item.fill(fMenu, -1);

						}
					}
				}
				return fMenu;
			}

			@Override
			public void dispose() {
				if (fMenu != null) {
					fMenu.dispose();
				}
			}

		});
		manager.add(providerSelection);
		manager.update(true);
	}

	/**
	 * Updates global actions depending on the current provider.
	 */
	private void fillGlobalActions() {
		final IActionBars actionBars = getViewSite().getActionBars();

		// Clear all global actions
		actionBars.clearGlobalActionHandlers();
		actionBars.updateActionBars();

		// Initialize global actions based on the provider
		provider.initGlobalActions(getViewSite());
	}

	/**
	 * @param event
	 */
	protected void handleCollapseAll(PropertyChangeEvent event) {
		provider.handleCollapseAll(event);
	}

	/**
	 * @param event
	 */
	protected void handleExpandAll(PropertyChangeEvent event) {
		provider.handleExpandAll(event);
	}

	private void fillContextMenu() {
		final MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {

			@Override
			public void menuAboutToShow(IMenuManager manager) {
				provider.initContextMenuActions(manager);
			}
		});
		final Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);

		if (getSite() instanceof IWorkbenchPartSite) {
			getSite().registerContextMenu(CONTEXT_MENU_ID, menuMgr, viewer);
		} else {
			((IPageSite) getSite()).registerContextMenu(CONTEXT_MENU_ID, menuMgr, viewer);
		}
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	/**
	 * Sets the new input or disables the viewer in case no editor is open
	 *
	 */
	private void update(final IFile iFile) {
		if (viewer != null) {
			final Control control = viewer.getControl();
			if ((control != null) && !control.isDisposed()) {

				if ((updateOutlineJob == null) || (updateOutlineJob.getState() == Job.NONE)) {
					updateOutlineJob = new UIJob(UPDATE_OUTLINE_VIEW) {

						@Override
						public IStatus runInUIThread(IProgressMonitor monitor) {

							if (viewer != null) {
								if ((viewer.getControl() != null) && !viewer.getControl().isDisposed()) {
									viewer.getControl().setRedraw(false);
									viewer.setContentProvider(provider.getTreeProvider());
									viewer.setLabelProvider(provider.getLabelProvider());
									updateActions();

									// Update input
									final Object input = iFile != null ? iFile : notAvailableInput;
									if (viewer.getInput() != input) {
										viewer.setInput(input);
									}
									if (iFile != null) {
										provider.handleUpdate(viewer, iFile);
									}

									viewer.getControl().setRedraw(true);
									viewer.getControl().setEnabled(true);
									viewer.refresh();
								}
							}
							return Status.OK_STATUS;
						}
					};
					updateOutlineJob.setPriority(Job.SHORT);
					updateOutlineJob.schedule();
				}
			}
		}
	}

	@Override
	public void selectionChanged(SelectionChangedEvent event) {
		provider.selectionChanged(event);
	}

	@Override
	public void treeCollapsed(TreeExpansionEvent event) {
		provider.treeCollapsed(event);
	}

	@Override
	public void treeExpanded(TreeExpansionEvent event) {
		provider.treeExpanded(event);
	}

	@Override
	public void propertyChanged(Object source, int propId) {
		update(curFile);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IPageChangedListener#pageChanged(org.eclipse.jface.dialogs.PageChangedEvent)
	 */
	@Override
	public void pageChanged(PageChangedEvent event) {
		setEditorActions(part);
	}

}
