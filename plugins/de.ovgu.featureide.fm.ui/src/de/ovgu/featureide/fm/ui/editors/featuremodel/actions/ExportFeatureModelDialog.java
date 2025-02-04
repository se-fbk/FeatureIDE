/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2017  FeatureIDE team, University of Magdeburg, Germany
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
package de.ovgu.featureide.fm.ui.editors.featuremodel.actions;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import de.ovgu.featureide.fm.core.io.FileSystem;
import de.ovgu.featureide.fm.core.io.Problem;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.ui.FMUIPlugin;
import de.ovgu.featureide.fm.ui.editors.ConstraintDialog.HeaderPanel;
import de.ovgu.featureide.fm.ui.editors.ConstraintDialog.HeaderPanel.HeaderDescriptionImage;

/**
 * Dialog for exporting a feature model in various formats.
 *
 * @author Andreas Gerasimow
 */
public class ExportFeatureModelDialog extends Dialog {

	private static String TITLE = "Export Feature Model";
	private static String PATH = "Path:";
	private static String BROWSE = "Browse...";
	private static String MODEL_NAME = "Model Name:";
	private static String SELECT_EXPORT_FORMAT = "Please select the format to export.";

	private final String defaultPath;
	private final String[] formatList;
	private final IFormatChecker formatChecker;
	private final IExporter exporter;

	private int selectedExporter = 0;
	private String selectedPath = "";
	private String selectedName = "";
	private boolean saveLogFile = true;
	private boolean saveFeatureModel = true;
	private ProblemList problems;

	private Button okButton;
	private HeaderPanel headerPanel;

	public ExportFeatureModelDialog(Shell parentShell, String defaultPath, String[] formatList, IFormatChecker formatChecker, IExporter exporter) {
		super(parentShell);
		this.defaultPath = defaultPath;
		this.formatList = formatList;
		this.formatChecker = formatChecker;
		this.exporter = exporter;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		super.createButtonsForButtonBar(parent);
		okButton = getButton(IDialogConstants.OK_ID);
		okButton.setEnabled(validate());
	}

	/**
	 * Sets the minimal size and the text in the title of the dialog.
	 *
	 * @param newshell
	 */
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(TITLE);
		newShell.setMinimumSize(new Point(450, 200));

		// header panel
		headerPanel = new HeaderPanel(newShell, true);
		headerPanel.setHeader(TITLE);
		headerPanel.setDetails(SELECT_EXPORT_FORMAT, HeaderDescriptionImage.NONE);
	}

	/**
	 * Creates the general layout of the dialog.
	 *
	 * @param parent
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		final Composite container = (Composite) super.createDialogArea(parent);
		((GridData) parent.getLayoutData()).grabExcessVerticalSpace = false;

		final GridLayout headLayout = new GridLayout();
		headLayout.numColumns = 3;
		headLayout.verticalSpacing = 5;
		container.setLayout(headLayout);

		computeProblems(0);

		// row 1
		final Label extensionLabel = new Label(container, SWT.NONE);
		extensionLabel.setText("Extension:");
		final Combo extensionDropDownMenu = new Combo(container, SWT.DROP_DOWN | SWT.READ_ONLY);
		extensionDropDownMenu.setItems(formatList);

		extensionDropDownMenu.addListener(SWT.Selection, (event) -> {
			computeProblems(extensionDropDownMenu.getSelectionIndex());
			selectedExporter = extensionDropDownMenu.getSelectionIndex();
		});
		extensionDropDownMenu.select(0);

		final GridData dropDownData = new GridData();
		dropDownData.horizontalAlignment = GridData.FILL;
		extensionDropDownMenu.setLayoutData(dropDownData);

		new Label(container, SWT.NONE); // empty placeholder

		// row 2
		final Label pathLabel = new Label(container, SWT.NONE);
		pathLabel.setText(PATH);

		final Text path = new Text(container, SWT.BORDER);
		path.addListener(SWT.Modify, (event) -> {
			selectedPath = path.getText();
			okButton.setEnabled(validate());
		});
		path.setText(defaultPath);
		final GridData pathData = new GridData();
		pathData.horizontalAlignment = GridData.FILL;
		pathData.grabExcessHorizontalSpace = true;
		path.setLayoutData(pathData);

		final Button browseButton = new Button(container, SWT.PUSH);
		browseButton.setText(BROWSE);
		final GridData browseButtonData = new GridData();
		browseButtonData.horizontalAlignment = GridData.FILL;
		browseButton.setLayoutData(browseButtonData);
		browseButton.addListener(SWT.Selection, (event) -> {
			final DirectoryDialog dialog = new DirectoryDialog(getShell());
			path.setText(dialog.open());
		});

		// row 3
		final Label nameLabel = new Label(container, SWT.NONE);
		nameLabel.setText(MODEL_NAME);
		final Text name = new Text(container, SWT.BORDER);
		name.addListener(SWT.Modify, (event) -> {
			selectedName = name.getText();
			okButton.setEnabled(validate());
		});
		name.setText("Untitled");
		final GridData nameData = new GridData();
		nameData.horizontalAlignment = GridData.FILL;
		name.setLayoutData(nameData);

		new Label(container, SWT.NONE); // empty placeholder

		// row 4
		new Label(container, SWT.NONE); // empty placeholder

		final Composite radiobuttons = new Composite(container, SWT.NONE);
		final FillLayout radiobuttonsLayout = new FillLayout();
		radiobuttonsLayout.type = SWT.VERTICAL;
		radiobuttons.setLayout(radiobuttonsLayout);

		final Button saveBoth = new Button(radiobuttons, SWT.RADIO);
		saveBoth.setText("Save feature model and logs");
		saveBoth.setSelection(true);
		saveBoth.addListener(SWT.Selection, (event) -> {
			if (saveBoth.getSelection()) {
				saveLogFile = true;
				saveFeatureModel = true;
			}
		});

		final Button saveLog = new Button(radiobuttons, SWT.RADIO);
		saveLog.setText("Save logs only");
		saveLog.addListener(SWT.Selection, (event) -> {
			if (saveLog.getSelection()) {
				saveLogFile = true;
				saveFeatureModel = false;
			}
		});

		final Button saveFile = new Button(radiobuttons, SWT.RADIO);
		saveFile.setText("Save feature model only");
		saveFile.addListener(SWT.Selection, (event) -> {
			if (saveFile.getSelection()) {
				saveLogFile = false;
				saveFeatureModel = true;
			}
		});

		return parent;
	}

	private void computeProblems(int formatIndex) {
		problems = formatChecker.checkFormat(formatIndex);
		headerPanel.setDetails(stringifyProblems(), problems.size() > formatIndex ? HeaderDescriptionImage.WARNING : HeaderDescriptionImage.NONE);
	}

	private String stringifyProblems() {
		if (problems.isEmpty()) {
			return "";
		}
		final StringBuilder problemsString = new StringBuilder();
		for (final Problem problem : problems) {
			problemsString.append("- ");
			problemsString.append(problem.getMessage());
			problemsString.append("\n");
		}
		problemsString.setLength(problemsString.length() - 1);
		return problemsString.toString();
	}

	private boolean validate() {
		return !selectedPath.isBlank() && !selectedName.isBlank();
	}

	@Override
	protected Control createContents(Composite parent) {
		super.createContents(parent);
		return parent;
	}

	@Override
	protected void okPressed() {
		if (saveLogFile) {
			writeProblemsToLogFile(Paths.get(selectedPath).resolve(selectedName + "_problems.log"));
		}
		if (saveFeatureModel) {
			exporter.export(selectedExporter, Paths.get(selectedPath), selectedName);
		}
		super.okPressed();
	}

	private void writeProblemsToLogFile(Path path) {
		try {
			FileSystem.write(path, stringifyProblems());
		} catch (final IOException e) {
			FMUIPlugin.getDefault().logError(e);
		}
	}

	@FunctionalInterface
	public interface IFormatChecker {

		ProblemList checkFormat(int formatIndex);
	}

	@FunctionalInterface
	public interface IExporter {

		void export(int formatIndex, Path path, String name);
	}
}
