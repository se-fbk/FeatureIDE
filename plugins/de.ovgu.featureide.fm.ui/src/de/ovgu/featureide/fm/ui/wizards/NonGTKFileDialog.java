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
package de.ovgu.featureide.fm.ui.wizards;

import java.nio.file.Paths;
import java.util.function.BooleanSupplier;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

import de.ovgu.featureide.fm.core.Preferences;

/**
 * Workaround for #1003
 *
 * @author Tobis Heß
 */
public class NonGTKFileDialog {

	public static final String WORKAROUND_INFO_REMEMBER = "de.ovgu.featureide.fm.ui.wizards.workaround_info_remember";

	private final JFileChooser chooser;
	private String selectedFile = null;
	private FileFilter[] filters;

	public NonGTKFileDialog(String path, String file) {
		chooser = new JFileChooser();
		chooser.setMultiSelectionEnabled(false);
		chooser.setSelectedFile(Paths.get(path).resolve(file).toFile());
	}

	/**
	 * @param fileNames
	 * @param fileExtensions
	 */
	public void setFilterNamesAndExtensions(String[] fileNames, String[] fileExtensions) {

		int k = 0;
		filters = new FileFilter[fileNames.length];

		for (int i = 0; i < fileNames.length; i++) {
			final String name = fileNames[i];
			final String[] extensions = fileExtensions[i].replaceAll("\\s*", "").split(",");

			final FileFilter filter = new FileNameExtensionFilter(name, extensions);
			filters[k++] = filter;
			chooser.addChoosableFileFilter(filter);
		}
	}

	/**
	 * @param defaultFormat
	 */
	public void setFilterIndex(int defaultFormat) {
		chooser.setFileFilter(chooser.getChoosableFileFilters()[defaultFormat]);
	}

	/**
	 * @param bs
	 * @return
	 */
	public void open(BooleanSupplier bs) {

		final NonGTKFileDialog dialog = this;

		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				final int ret = chooser.showSaveDialog(null);
				if (ret == JFileChooser.APPROVE_OPTION) {
					final FileNameExtensionFilter filter = (FileNameExtensionFilter) chooser.getFileFilter();
					final String extension = filter.getExtensions()[0].replaceAll("\\*", "");
					dialog.selectedFile = chooser.getSelectedFile().getAbsolutePath();
					dialog.selectedFile += dialog.selectedFile.endsWith(extension) ? "" : extension;
				}
				bs.getAsBoolean();
			}
		});
	}

	/**
	 * @return
	 */
	public int getFilterIndex() {

		final FileFilter filter = chooser.getFileFilter();

		for (int i = 0; i < filters.length; i++) {
			if (filter.equals(filters[i])) {
				return i;
			}
		}

		return -1;
	}

	public static void spawnInfo() {

		if (Boolean.parseBoolean(Preferences.getPref(WORKAROUND_INFO_REMEMBER, "false"))) {
			return;
		}

		final MessageDialogWithToggle dialog = MessageDialogWithToggle.open(MessageDialog.INFORMATION, Display.getCurrent().getActiveShell(),
				"Workaround information",
				"Due to a feature breaking bug in the GTK framework, FeatureIDE has to employ a workaround to maintain functionality. See also bug #1003 in the FeatureIDE bug tracker",
				"Do not show this message again", true, null, null, SWT.NONE);

		Preferences.store(WORKAROUND_INFO_REMEMBER, String.valueOf(dialog.getToggleState()));
	}

	/**
	 * @return
	 */
	public String getSelectedFile() {
		return selectedFile;
	}

}
