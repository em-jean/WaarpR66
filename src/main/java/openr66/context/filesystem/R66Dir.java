/**
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3.0 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package openr66.context.filesystem;

import goldengate.common.command.exception.CommandAbstractException;
import goldengate.common.command.exception.Reply550Exception;
import goldengate.common.command.exception.Reply553Exception;
import goldengate.common.file.FileInterface;
import goldengate.common.file.filesystembased.FilesystemBasedDirImpl;
import goldengate.common.file.filesystembased.FilesystemBasedOptsMLSxImpl;
import goldengate.common.file.filesystembased.specific.FilesystemBasedCommonsIo;
import goldengate.common.file.filesystembased.specific.FilesystemBasedDirJdkAbstract;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import openr66.context.R66Session;
import openr66.protocol.configuration.Configuration;

/**
 * Directory representation
 *
 * @author frederic bregier
 *
 */
public class R66Dir extends FilesystemBasedDirImpl {

    /**
     * @param session
     */
    public R66Dir(R66Session session) {
        super(session, new FilesystemBasedOptsMLSxImpl());
    }

    /*
     * (non-Javadoc)
     *
     * @see goldengate.common.file.DirInterface#newFile(java.lang.String,
     * boolean)
     */
    @Override
    public R66File newFile(String path, boolean append)
            throws CommandAbstractException {
        return new R66File((R66Session) getSession(), this, path, append);
    }

    /**
     * Same as setUnique() except that File will be prefixed by id and postfixed by filename
     *
     * @param prefix
     * @param filename
     * @return the R66File with a unique filename and a temporary extension
     * @throws CommandAbstractException
     */
    public synchronized R66File setUniqueFile(long prefix, String filename)
            throws CommandAbstractException {
        checkIdentify();
        File file = null;
        String prename = prefix + "_";
        String basename = R66File.getBasename(filename);
        try {
            file = File.createTempFile(prename, "_" + basename +
                    Configuration.EXT_R66, getFileFromPath(currentDir));
        } catch (IOException e) {
            throw new Reply550Exception("Cannot create unique file from " +
                    basename);
        }
        String currentFile = getRelativePath(file);
        return newFile(normalizePath(currentFile), false);
    }

    /**
     *
     * @param file
     * @return the final unique basename without the temporary extension
     */
    public String getFinalUniqueFilename(R66File file) {
        String finalpath = file.getBasename();
        int pos = finalpath.lastIndexOf(Configuration.EXT_R66);
        if (pos > 0) {
            finalpath = finalpath.substring(0, pos);
        }
        return finalpath;
    }

    /**
     * Finds all files matching a wildcard expression (based on '?', '~' or
     * '*') but without checking BusinessPath, thus returning absolute path.
     *
     * @param pathWithWildcard
     *            The wildcard expression with a business path.
     * @return List of String as relative paths matching the wildcard
     *         expression. Those files are tested as valid from business point
     *         of view. If Wildcard support is not active, if the path contains
     *         any wildcards, it will throw an error.
     * @throws CommandAbstractException
     */
    protected List<String> wildcardFilesNoCheck(String pathWithWildcard)
            throws CommandAbstractException {
        List<String> resultPaths = new ArrayList<String>();
        // First check if pathWithWildcard contains wildcards
        if (!(pathWithWildcard.contains("*") || pathWithWildcard.contains("?") || pathWithWildcard
                .contains("~"))) {
            // No so simply return the list containing this path
            resultPaths.add(pathWithWildcard);
            return resultPaths;
        }
        // Do we support Wildcard path
        if (!FilesystemBasedDirJdkAbstract.ueApacheCommonsIo) {
            throw new Reply553Exception("Wildcards in pathname is not allowed");
        }
        File wildcardFile = new File(pathWithWildcard);
        // Split wildcard path into subdirectories.
        List<String> subdirs = new ArrayList<String>();
        while (wildcardFile != null) {
            File parent = wildcardFile.getParentFile();
            if (parent == null) {
                subdirs.add(0, wildcardFile.getPath());
                break;
            }
            subdirs.add(0, wildcardFile.getName());
            wildcardFile = parent;
        }
        List<File> basedPaths = new ArrayList<File>();
        // First set root
        basedPaths.add(new File(subdirs.get(0)));
        int i = 1;
        // For each wilcard subdirectory
        while (i < subdirs.size()) {
            // Set current filter
            FileFilter fileFilter = FilesystemBasedCommonsIo
                    .getWildcardFileFilter(subdirs.get(i));
            List<File> newBasedPaths = new ArrayList<File>();
            // Look for matches in all the current search paths
            for (File dir: basedPaths) {
                if (dir.isDirectory()) {
                    for (File match: dir.listFiles(fileFilter)) {
                        newBasedPaths.add(match);
                    }
                }
            }
            // base Search Path changes now
            basedPaths = newBasedPaths;
            i ++;
        }
        // Valid each file first
        for (File file: basedPaths) {
            resultPaths.add(file.getAbsolutePath());
        }
        return resultPaths;
    }

    /**
     * Create a new file according to the path without checking BusinessPath,
     * so as external File.
     * @param path
     * @return the File created
     * @throws CommandAbstractException
     */
    public FileInterface setFileNoCheck(String path)
        throws CommandAbstractException {
        checkIdentify();
        String newpath = consolidatePath(path);
        List<String> paths = wildcardFilesNoCheck(newpath);
        if (paths.size() != 1) {
            throw new Reply550Exception("FileInterface not found: " +
                    paths.size() + " founds");
        }
        String extDir = paths.get(0);
        return newFile(extDir, false);
    }

    @Override
    public String toString() {
        return "Dir: " + currentDir;
    }
}
