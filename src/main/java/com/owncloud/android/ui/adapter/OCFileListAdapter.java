/*
 * ownCloud Android client application
 *
 * @author Bartek Przybylski
 * @author Tobias Kaminsky
 * @author David A. Velasco
 * @author masensio
 * Copyright (C) 2011  Bartek Przybylski
 * Copyright (C) 2016 ownCloud Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.owncloud.android.ui.adapter;


import android.accounts.Account;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.owncloud.android.R;
import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.datamodel.ThumbnailsCacheManager;
import com.owncloud.android.datamodel.VirtualFolderType;
import com.owncloud.android.db.PreferenceManager;
import com.owncloud.android.db.ProviderMeta;
import com.owncloud.android.files.services.FileDownloader;
import com.owncloud.android.files.services.FileUploader;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.files.ReadRemoteFileOperation;
import com.owncloud.android.lib.resources.files.RemoteFile;
import com.owncloud.android.lib.resources.shares.OCShare;
import com.owncloud.android.lib.resources.shares.ShareType;
import com.owncloud.android.operations.RemoteOperationFailedException;
import com.owncloud.android.services.OperationsService;
import com.owncloud.android.ui.activity.ComponentsGetter;
import com.owncloud.android.ui.fragment.ExtendedListFragment;
import com.owncloud.android.ui.interfaces.OCFileListFragmentInterface;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.FileSortOrder;
import com.owncloud.android.utils.FileStorageUtils;
import com.owncloud.android.utils.MimeTypeUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Vector;


/**
 * This Adapter populates a ListView with all files and folders in an ownCloud
 * instance.
 */
public class OCFileListAdapter extends RecyclerView.Adapter<OCFileListAdapter.OCFileListGridViewHolder> {

    public static final int showFilenameColumnThreshold = 4;
    private final FileDownloader.FileDownloaderBinder downloaderBinder;
    private final FileUploader.FileUploaderBinder uploaderBinder;
    private final OperationsService.OperationsServiceBinder operationsServiceBinder;
    private Context mContext;
    private Vector<OCFile> mFilesAll = new Vector<>();
    private Vector<OCFile> mFiles = new Vector<>();
    private boolean mJustFolders;
    private boolean mHideItemOptions;
    private boolean gridView = false;

    private FileDataStorageManager mStorageManager;
    private Account mAccount;
    private OCFileListFragmentInterface ocFileListFragmentInterface;

    private FilesFilter mFilesFilter;
    private OCFile currentDirectory;
    private static final String TAG = OCFileListAdapter.class.getSimpleName();

    private ArrayList<ThumbnailsCacheManager.ThumbnailGenerationTask> asyncTasks = new ArrayList<>();

    public OCFileListAdapter(boolean justFolders, Context context, ComponentsGetter transferServiceGetter,
                             OCFileListFragmentInterface ocFileListFragmentInterface, boolean argHideItemOptions,
                             boolean gridView) {

        this.ocFileListFragmentInterface = ocFileListFragmentInterface;
        mJustFolders = justFolders;
        mContext = context;
        mAccount = AccountUtils.getCurrentOwnCloudAccount(mContext);
        mHideItemOptions = argHideItemOptions;
        this.gridView = gridView;

        downloaderBinder = transferServiceGetter.getFileDownloaderBinder();
        uploaderBinder = transferServiceGetter.getFileUploaderBinder();
        operationsServiceBinder = transferServiceGetter.getOperationsServiceBinder();

        // initialise thumbnails cache on background thread
        new ThumbnailsCacheManager.InitDiskCacheTask().execute();
    }

    // TODO recycler
//    @Override
//    public boolean areAllItemsEnabled() {
//        return true;
//    }
//
//    @Override
//    public boolean isEnabled(int position) {
//        return true;
//    }
//
//    @Override
//    public int getCount() {
//        return mFiles != null ? mFiles.size() : 0;
//    }
//
//    @Override
//    public Object getItem(int position) {
//        if (mFiles == null || mFiles.size() <= position) {
//            return null;
//        }
//        return mFiles.get(position);
//    }

    public void setFavoriteAttributeForItemID(String fileId, boolean favorite) {
        for (int i = 0; i < mFiles.size(); i++) {
            if (mFiles.get(i).getRemoteId().equals(fileId)) {
                mFiles.get(i).setFavorite(favorite);
                break;
            }
        }

        for (int i = 0; i < mFilesAll.size(); i++) {
            if (mFilesAll.get(i).getRemoteId().equals(fileId)) {
                mFilesAll.get(i).setFavorite(favorite);
                break;
            }
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    public void setEncryptionAttributeForItemID(String fileId, boolean encrypted) {
        for (int i = 0; i < mFiles.size(); i++) {
            if (mFiles.get(i).getRemoteId().equals(fileId)) {
                mFiles.get(i).setEncrypted(encrypted);
                break;
            }
        }

        for (int i = 0; i < mFilesAll.size(); i++) {
            if (mFilesAll.get(i).getRemoteId().equals(fileId)) {
                mFilesAll.get(i).setEncrypted(encrypted);
                break;
            }
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public long getItemId(int position) {
        if (mFiles == null || mFiles.size() <= position) {
            return 0;
        }
        return mFiles.get(position).getFileId();
    }

    @Override
    public int getItemCount() {
        return mFiles.size();
    }

    @Override
    public OCFileListGridViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (gridView) {
            View itemView = LayoutInflater.from(mContext).inflate(R.layout.grid_item, parent, false);
            return new OCFileListGridViewHolder(itemView);
        } else {
            View itemView = LayoutInflater.from(mContext).inflate(R.layout.list_item, parent, false);
            return new OCFileListItemViewHolder(itemView);
        }
    }

    @Override
    public void onBindViewHolder(OCFileListGridViewHolder holder, int position) {
        // TODO handle if file is null: if (mFiles != null && mFiles.size() > position)
        OCFile file = mFiles.get(position);

        boolean gridImage = MimeTypeUtil.isImage(file) || MimeTypeUtil.isVideo(file);

        holder.thumbnail.setTag(file.getFileId());
        setThumbnail(file, holder.thumbnail);

        holder.itemLayout.setOnClickListener(v -> ocFileListFragmentInterface.onItemClicked(file));

        holder.fileName.setText(file.getFileName());

        if (holder instanceof OCFileListItemViewHolder) {
            OCFileListItemViewHolder itemViewHolder = (OCFileListItemViewHolder) holder;
            itemViewHolder.fileSize.setText(DisplayUtils.bytesToHumanReadable(file.getFileLength()));
            itemViewHolder.lastModification.setText(DisplayUtils.getRelativeTimestamp(mContext,
                    file.getModificationTimestamp()));

            itemViewHolder.overflowMenu.setOnClickListener(view -> ocFileListFragmentInterface
                    .onOverflowIconClicked(file, view));
        }

//        private final ImageView shared;
//        private final ImageView checkbox;
//        private final ImageView overflowMenu;

        holder.localFileIndicator.setVisibility(View.INVISIBLE);   // default first

        if (operationsServiceBinder != null && operationsServiceBinder.isSynchronizing(mAccount, file)) {
            //synchronizing
            holder.localFileIndicator.setImageResource(R.drawable.ic_synchronizing);
            holder.localFileIndicator.setVisibility(View.VISIBLE);

        } else if (downloaderBinder != null && downloaderBinder.isDownloading(mAccount, file)) {
            // downloading
            holder.localFileIndicator.setImageResource(R.drawable.ic_synchronizing);
            holder.localFileIndicator.setVisibility(View.VISIBLE);

        } else if (uploaderBinder != null && uploaderBinder.isUploading(mAccount, file)) {
            //uploading
            holder.localFileIndicator.setImageResource(R.drawable.ic_synchronizing);
            holder.localFileIndicator.setVisibility(View.VISIBLE);

        } else if (file.getEtagInConflict() != null) {
            // conflict
            holder.localFileIndicator.setImageResource(R.drawable.ic_synchronizing_error);
            holder.localFileIndicator.setVisibility(View.VISIBLE);

        } else if (file.isDown()) {
            holder.localFileIndicator.setImageResource(R.drawable.ic_synced);
            holder.localFileIndicator.setVisibility(View.VISIBLE);
        }

        holder.favorite.setVisibility(file.getIsFavorite() ? View.VISIBLE : View.GONE);
        holder.offlineIcon.setVisibility(file.isAvailableOffline() ? View.VISIBLE : View.GONE);

        holder.checkbox.setVisibility(View.GONE);

//        if (ocFileListFragmentInterface.getColumnSize() > showFilenameColumnThreshold
//                            && viewType == ViewType.GRID_ITEM) {
//                        fileName.setVisibility(View.GONE);
//                    }
    }

    private void setThumbnail(OCFile file, ImageView thumbnailView) {
        if (file.isFolder()) {
            thumbnailView.setImageDrawable(MimeTypeUtil.getFolderTypeIcon(file.isSharedWithMe() ||
                    file.isSharedWithSharee(), file.isSharedViaLink(), file.isEncrypted()));
        } else {
            if ((MimeTypeUtil.isImage(file) || MimeTypeUtil.isVideo(file)) && file.getRemoteId() != null) {
                // Thumbnail in cache?
                Bitmap thumbnail = ThumbnailsCacheManager.getBitmapFromDiskCache(
                        ThumbnailsCacheManager.PREFIX_THUMBNAIL + String.valueOf(file.getRemoteId())
                );

                if (thumbnail != null && !file.needsUpdateThumbnail()) {
                    if (MimeTypeUtil.isVideo(file)) {
                        Bitmap withOverlay = ThumbnailsCacheManager.addVideoOverlay(thumbnail);
                        thumbnailView.setImageBitmap(withOverlay);
                    } else {
                        thumbnailView.setImageBitmap(thumbnail);
                    }
                } else {
                    // generate new thumbnail
                    if (ThumbnailsCacheManager.cancelPotentialThumbnailWork(file, thumbnailView)) {
                        try {
                            final ThumbnailsCacheManager.ThumbnailGenerationTask task =
                                    new ThumbnailsCacheManager.ThumbnailGenerationTask(thumbnailView, mStorageManager,
                                            mAccount, asyncTasks);

                            if (thumbnail == null) {
                                if (MimeTypeUtil.isVideo(file)) {
                                    thumbnail = ThumbnailsCacheManager.mDefaultVideo;
                                } else {
                                    thumbnail = ThumbnailsCacheManager.mDefaultImg;
                                }
                            }
                            final ThumbnailsCacheManager.AsyncThumbnailDrawable asyncDrawable =
                                    new ThumbnailsCacheManager.AsyncThumbnailDrawable(mContext.getResources(),
                                            thumbnail, task);
                            thumbnailView.setImageDrawable(asyncDrawable);
                            asyncTasks.add(task);
                            task.execute(new ThumbnailsCacheManager.ThumbnailGenerationTaskObject(file,
                                    file.getRemoteId()));
                        } catch (IllegalArgumentException e) {
                            Log_OC.d(TAG, "ThumbnailGenerationTask : " + e.getMessage());
                        }
                    }
                }

                if (file.getMimetype().equalsIgnoreCase("image/png")) {
                    thumbnailView.setBackgroundColor(mContext.getResources().getColor(R.color.background_color));
                }
            } else {
                thumbnailView.setImageDrawable(MimeTypeUtil.getFileTypeIcon(file.getMimetype(), file.getFileName(),
                        mAccount));
            }
        }
    }

    public OCFile getItem(int position) {
        return mFiles.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    // TODO recycler
//    @Override
//    public View getView(int position, View convertView, ViewGroup parent) {

//            ImageView checkBoxV = view.findViewById(R.id.custom_checkbox);
//            view.setBackgroundColor(Color.WHITE);
//
//            AbsListView parentList = (AbsListView) parent;
//
//            if (parentList.getChoiceMode() != AbsListView.CHOICE_MODE_NONE && parentList.getCheckedItemCount() > 0) {
//                if (parentList.isItemChecked(position)) {
//                    view.setBackgroundColor(mContext.getResources().getColor(R.color.selected_item_background));
//                    checkBoxV.setImageDrawable(ThemeUtils.tintDrawable(R.drawable.ic_checkbox_marked,
//                            ThemeUtils.primaryColor()));
//                } else {
//                    view.setBackgroundColor(Color.WHITE);
//                    checkBoxV.setImageResource(R.drawable.ic_checkbox_blank_outline);
//                }
//                checkBoxV.setVisibility(View.VISIBLE);
//                hideShareIcon(view);
//                hideOverflowMenuIcon(view, viewType);
//            } else {
//                checkBoxV.setVisibility(View.GONE);
//
//                if (mHideItemOptions) {
//                    ImageView sharedIconView = view.findViewById(R.id.sharedIcon);
//                    sharedIconView.setVisibility(View.GONE);
//
//                    ImageView overflowIndicatorView = view.findViewById(R.id.overflow_menu);
//                    overflowIndicatorView.setVisibility(View.GONE);
//                } else {
//                    showShareIcon(view, file);
//                    showOverflowMenuIcon(view, file, viewType);
//                }
//            }

    private void showShareIcon(View view, OCFile file) {
        ImageView sharedIconV = view.findViewById(R.id.sharedIcon);
        sharedIconV.setVisibility(View.VISIBLE);
        if (file.isSharedWithSharee() || file.isSharedWithMe()) {
            sharedIconV.setImageResource(R.drawable.shared_via_users);
        } else if (file.isSharedViaLink()) {
            sharedIconV.setImageResource(R.drawable.shared_via_link);
        } else {
            sharedIconV.setImageResource(R.drawable.ic_unshared);
        }
        sharedIconV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ocFileListFragmentInterface.onShareIconClick(file);
            }
        });
    }

    private void hideShareIcon(View view) {
        view.findViewById(R.id.sharedIcon).setVisibility(View.GONE);
    }

    private void hideOverflowMenuIcon(View view, ViewType viewType) {
        if (ViewType.LIST_ITEM.equals(viewType)) {
            ImageView overflowIndicatorV = view.findViewById(R.id.overflow_menu);
            overflowIndicatorV.setVisibility(View.GONE);
        }
    }

    // todo recycler
//    @Override
//    public int getViewTypeCount() {
//        return 1;
//    }
//
//    @Override
//    public boolean hasStableIds() {
//        return true;
//    }
//
//    @Override
//    public boolean isEmpty() {
//        return (mFiles == null || mFiles.isEmpty());
//    }

    /**
     * Change the adapted directory for a new one
     *
     * @param directory             New folder to adapt. Can be NULL, meaning
     *                              "no content to adapt".
     * @param updatedStorageManager Optional updated storage manager; used to replace
     *                              mStorageManager if is different (and not NULL)
     */
    public void swapDirectory(OCFile directory, FileDataStorageManager updatedStorageManager
            , boolean onlyOnDevice) {
        if (updatedStorageManager != null && !updatedStorageManager.equals(mStorageManager)) {
            mStorageManager = updatedStorageManager;
            mAccount = AccountUtils.getCurrentOwnCloudAccount(mContext);
        }
        if (mStorageManager != null) {
            mFiles = mStorageManager.getFolderContent(directory, onlyOnDevice);

            if (mJustFolders) {
                mFiles = getFolders(mFiles);
            }
            if (!PreferenceManager.showHiddenFilesEnabled(mContext)) {
                mFiles = filterHiddenFiles(mFiles);
            }
            FileSortOrder sortOrder = PreferenceManager.getSortOrder(mContext, directory);
            mFiles = sortOrder.sortCloudFiles(mFiles);
            mFilesAll.clear();
            mFilesAll.addAll(mFiles);

            currentDirectory = directory;
        } else {
            mFiles.clear();
            mFilesAll.clear();
        }

        notifyDataSetChanged();
    }

    private void searchForLocalFileInDefaultPath(OCFile file) {
        if (file.getStoragePath() == null && !file.isFolder()) {
            File f = new File(FileStorageUtils.getDefaultSavePathFor(mAccount.name, file));
            if (f.exists()) {
                file.setStoragePath(f.getAbsolutePath());
                file.setLastSyncDateForData(f.lastModified());
            }
        }
    }

    public void setData(ArrayList<Object> objects, ExtendedListFragment.SearchType searchType,
                        FileDataStorageManager storageManager, OCFile folder) {
        if (storageManager != null && mStorageManager == null) {
            mStorageManager = storageManager;
        }
        mFiles.clear();

        // early exit
        if (objects.size() > 0 && mStorageManager != null) {
            if (searchType.equals(ExtendedListFragment.SearchType.SHARED_FILTER)) {
                parseShares(objects);
            } else {
                parseVirtuals(objects, searchType);
            }
        }

        if (!searchType.equals(ExtendedListFragment.SearchType.PHOTO_SEARCH) &&
                !searchType.equals(ExtendedListFragment.SearchType.PHOTOS_SEARCH_FILTER) &&
                !searchType.equals(ExtendedListFragment.SearchType.RECENTLY_MODIFIED_SEARCH) &&
                !searchType.equals(ExtendedListFragment.SearchType.RECENTLY_MODIFIED_SEARCH_FILTER)) {
            FileSortOrder sortOrder = PreferenceManager.getSortOrder(mContext, folder);
            mFiles = sortOrder.sortCloudFiles(mFiles);
        } else {
            mFiles = FileStorageUtils.sortOcFolderDescDateModified(mFiles);
        }

        mFilesAll = new Vector<>();
        mFilesAll.addAll(mFiles);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
                ocFileListFragmentInterface.finishedFiltering();
            }
        });
    }

    private void parseShares(ArrayList<Object> objects) {
        ArrayList<OCShare> shares = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            // check type before cast as of long running data fetch it is possible that old result is filled
            if (objects.get(i) instanceof OCShare) {
                OCShare ocShare = (OCShare) objects.get(i);

                shares.add(ocShare);

                // get ocFile from Server to have an up-to-date copy
                ReadRemoteFileOperation operation = new ReadRemoteFileOperation(ocShare.getPath());
                RemoteOperationResult result = operation.execute(mAccount, mContext);
                if (result.isSuccess()) {
                    OCFile file = FileStorageUtils.fillOCFile((RemoteFile) result.getData().get(0));
                    searchForLocalFileInDefaultPath(file);
                    file = mStorageManager.saveFileWithParent(file, mContext);

                    ShareType newShareType = ocShare.getShareType();
                    if (newShareType == ShareType.PUBLIC_LINK) {
                        file.setShareViaLink(true);
                    } else if (newShareType == ShareType.USER || newShareType == ShareType.GROUP ||
                            newShareType == ShareType.EMAIL || newShareType == ShareType.FEDERATED) {
                        file.setShareWithSharee(true);
                    }

                    mStorageManager.saveFile(file);

                    if (!mFiles.contains(file)) {
                        mFiles.add(file);
                    }
                } else {
                    Log_OC.e(TAG, "Error in getting prop for file: " + ocShare.getPath());
                }
            }
        }
        mStorageManager.saveShares(shares);
    }

    private void parseVirtuals(ArrayList<Object> objects, ExtendedListFragment.SearchType searchType) {
        VirtualFolderType type;
        boolean onlyImages = false;
        switch (searchType) {
            case FAVORITE_SEARCH:
                type = VirtualFolderType.FAVORITE;
                break;
            case PHOTO_SEARCH:
                type = VirtualFolderType.PHOTOS;
                onlyImages = true;
                break;
            default:
                type = VirtualFolderType.NONE;
                break;
        }

        mStorageManager.deleteVirtuals(type);

        ArrayList<ContentValues> contentValues = new ArrayList<>();

        for (int i = 0; i < objects.size(); i++) {
            OCFile ocFile = FileStorageUtils.fillOCFile((RemoteFile) objects.get(i));
            searchForLocalFileInDefaultPath(ocFile);

            try {
                ocFile = mStorageManager.saveFileWithParent(ocFile, mContext);

                if (!onlyImages || MimeTypeUtil.isImage(ocFile)) {
                    mFiles.add(ocFile);
                }

                ContentValues cv = new ContentValues();
                cv.put(ProviderMeta.ProviderTableMeta.VIRTUAL_TYPE, type.toString());
                cv.put(ProviderMeta.ProviderTableMeta.VIRTUAL_OCFILE_ID, ocFile.getFileId());

                contentValues.add(cv);
            } catch (RemoteOperationFailedException e) {
                Log_OC.e(TAG, "Error saving file with parent" + e.getMessage(), e);
            }
        }

        mStorageManager.saveVirtuals(type, contentValues);
    }

    /**
     * Filter for getting only the folders
     *
     * @param files Collection of files to filter
     * @return Folders in the input
     */
    public Vector<OCFile> getFolders(Vector<OCFile> files) {
        Vector<OCFile> ret = new Vector<>();
        OCFile current;
        for (int i = 0; i < files.size(); i++) {
            current = files.get(i);
            if (current.isFolder()) {
                ret.add(current);
            }
        }
        return ret;
    }


    public void setSortOrder(OCFile folder, FileSortOrder sortOrder) {
        PreferenceManager.setSortOrder(mContext, folder, sortOrder);
        mFiles = sortOrder.sortCloudFiles(mFiles);
        notifyDataSetChanged();
    }


    public ArrayList<OCFile> getCheckedItems(AbsListView parentList) {
        SparseBooleanArray checkedPositions = parentList.getCheckedItemPositions();
        ArrayList<OCFile> files = new ArrayList<>();
        Object item;
        for (int i = 0; i < checkedPositions.size(); i++) {
            if (checkedPositions.valueAt(i)) {
                item = getItem(checkedPositions.keyAt(i));
                if (item != null) {
                    files.add((OCFile) item);
                }
            }
        }
        return files;
    }

    public Vector<OCFile> getFiles() {
        return mFiles;
    }

    public Filter getFilter() {
        if (mFilesFilter == null) {
            mFilesFilter = new FilesFilter();
        }
        return mFilesFilter;
    }

    private class FilesFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            Vector<OCFile> filteredFiles = new Vector<>();

            if (!TextUtils.isEmpty(constraint)) {
                for (int i = 0; i < mFilesAll.size(); i++) {
                    OCFile currentFile = mFilesAll.get(i);
                    if (currentFile.getParentRemotePath().equals(currentDirectory.getRemotePath()) &&
                            currentFile.getFileName().toLowerCase(Locale.getDefault()).contains(
                                    constraint.toString().toLowerCase(Locale.getDefault())) &&
                            !filteredFiles.contains(currentFile)) {
                        filteredFiles.add(currentFile);
                    }
                }
            }

            results.values = filteredFiles;
            results.count = filteredFiles.size();

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, Filter.FilterResults results) {
            Vector<OCFile> ocFiles = (Vector<OCFile>) results.values;
            mFiles.clear();
            if (ocFiles != null && ocFiles.size() > 0) {
                mFiles.addAll(ocFiles);
                if (!PreferenceManager.showHiddenFilesEnabled(mContext)) {
                    mFiles = filterHiddenFiles(mFiles);
                }
                FileSortOrder sortOrder = PreferenceManager.getSortOrder(mContext, null);
                mFiles = sortOrder.sortCloudFiles(mFiles);
            }

            notifyDataSetChanged();
            ocFileListFragmentInterface.finishedFiltering();

        }
    }


    /**
     * Filter for hidden files
     *
     * @param files Collection of files to filter
     * @return Non-hidden files
     */
    public Vector<OCFile> filterHiddenFiles(Vector<OCFile> files) {
        Vector<OCFile> ret = new Vector<>();
        OCFile current;
        for (int i = 0; i < files.size(); i++) {
            current = files.get(i);
            if (!current.isHidden() && !ret.contains(current)) {
                ret.add(current);
            }
        }
        return ret;
    }

    public void cancelAllPendingTasks() {
        for (ThumbnailsCacheManager.ThumbnailGenerationTask task : asyncTasks) {
            if (task != null) {
                task.cancel(true);
                if (task.getGetMethod() != null) {
                    Log_OC.d(TAG, "cancel: abort get method directly");
                    task.getGetMethod().abort();
                }
            }
        }

        asyncTasks.clear();
    }

    public void setGridView(boolean bool) {
        gridView = bool;
    }

    static class OCFileListItemViewHolder extends OCFileListGridViewHolder {
        private final TextView fileSize;
        private final TextView lastModification;
        private final ImageView overflowMenu;

        private OCFileListItemViewHolder(View itemView) {
            super(itemView);

            fileSize = itemView.findViewById(R.id.file_size);
            lastModification = itemView.findViewById(R.id.last_mod);
            overflowMenu = itemView.findViewById(R.id.overflow_menu);
        }
    }

    static class OCFileListGridViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final ImageView favorite;
        private final ImageView offlineIcon;
        private final ImageView localFileIndicator;
        private final TextView fileName;
        private final ImageView shared;
        private final ImageView checkbox;
        private final LinearLayout itemLayout;

        private OCFileListGridViewHolder(View itemView) {
            super(itemView);

            thumbnail = itemView.findViewById(R.id.thumbnail);
            favorite = itemView.findViewById(R.id.favorite_action);
            offlineIcon = itemView.findViewById(R.id.keptOfflineIcon);
            localFileIndicator = itemView.findViewById(R.id.localFileIndicator);
            fileName = itemView.findViewById(R.id.Filename);
            shared = itemView.findViewById(R.id.sharedIcon);
            checkbox = itemView.findViewById(R.id.custom_checkbox);
            itemLayout = itemView.findViewById(R.id.ListItemLayout);
        }
    }
}