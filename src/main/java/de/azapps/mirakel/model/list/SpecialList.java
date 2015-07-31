/*******************************************************************************
 * Mirakel is an Android App for managing your ToDo-Lists
 *
 *   Copyright (c) 2013-2015 Anatolij Zelenin, Georg Semmler.
 *
 *       This program is free software: you can redistribute it and/or modify
 *       it under the terms of the GNU General Public License as published by
 *       the Free Software Foundation, either version 3 of the License, or
 *       any later version.
 *
 *       This program is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *       GNU General Public License for more details.
 *
 *       You should have received a copy of the GNU General Public License
 *       along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package de.azapps.mirakel.model.list;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Parcel;
import android.support.annotation.NonNull;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

import java.util.List;

import de.azapps.mirakel.DefinitionsHelper.SYNC_STATE;
import de.azapps.mirakel.model.DatabaseHelper;
import de.azapps.mirakel.model.MirakelInternalContentProvider;
import de.azapps.mirakel.model.ModelBase;
import de.azapps.mirakel.model.R;
import de.azapps.mirakel.model.account.AccountMirakel;
import de.azapps.mirakel.model.list.meta.SpecialListsBaseProperty;
import de.azapps.mirakel.model.query_builder.CursorGetter;
import de.azapps.mirakel.model.query_builder.CursorWrapper;
import de.azapps.mirakel.model.query_builder.MirakelQueryBuilder;
import de.azapps.mirakel.model.query_builder.MirakelQueryBuilder.Operation;
import de.azapps.mirakel.model.query_builder.MirakelQueryBuilder.Sorting;
import de.azapps.mirakel.model.task.Task;
import de.azapps.tools.FileUtils;
import de.azapps.tools.Log;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.fromNullable;
import static de.azapps.tools.OptionalUtils.transformOrNull;

public class SpecialList extends ListMirakel {
    private boolean active;
    @NonNull
    private Optional<ListMirakel> defaultList = absent();
    @NonNull
    private Optional<Integer> defaultDate;
    @NonNull
    private Optional<SpecialListsBaseProperty> where = absent();
    private String whereString;

    public static final Uri URI = MirakelInternalContentProvider.SPECIAL_LISTS_URI;

    public boolean isActive() {
        return this.active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    @Override
    @NonNull
    public MirakelQueryBuilder getWhereQueryForTasks() {
        final MirakelQueryBuilder mirakelQueryBuilder = packWhere(getWhere());
        if (getAccount().getType() != AccountMirakel.ACCOUNT_TYPES.ALL) {
            mirakelQueryBuilder.and(ACCOUNT_ID, Operation.EQ, getAccount());
        }
        return mirakelQueryBuilder;
    }

    @Override
    protected Uri getUri() {
        return URI;
    }

    @Override
    public long getId() {
        return -1L * super.getId();
    }

    @Override
    protected void setId(final long newId) {
        super.setId(Math.abs(newId));
    }

    public Optional<SpecialListsBaseProperty> getWhere() {
        if (!where.isPresent()) {
            where = SpecialListsWhereDeserializer.deserializeWhere(whereString, getName());
        }
        return this.where;
    }

    public void setWhere(final @NonNull Optional<SpecialListsBaseProperty> where) {
        this.whereString = serializeWhere(where);
        this.where = where;
    }

    public ListMirakel getDefaultList() {
        if (!this.defaultList.isPresent()) {
            return ListMirakel.safeFirst();
        } else {
            return this.defaultList.get();
        }
    }

    public void setDefaultList(@NonNull final Optional<ListMirakel> defaultList) {
        this.defaultList = defaultList;
    }

    @NonNull
    public Optional<Integer> getDefaultDate() {
        return defaultDate;
    }

    public void setDefaultDate(@NonNull Optional<Integer> defaultDate) {
        this.defaultDate = defaultDate;
    }

    SpecialList(final long id, @NonNull final String name,
                final @NonNull Optional<SpecialListsBaseProperty> whereQuery,
                final boolean active, @NonNull final Optional<ListMirakel> defaultList,
                final @NonNull Optional<Integer> defaultDate, final SORT_BY sort_by,
                final SYNC_STATE sync_state, final int color, final int lft,
                final int rgt, @NonNull final Optional<Uri> iconPath) {
        super(-id, name, sort_by, "", "", sync_state, 0, 0, color,
              AccountMirakel.getLocal(), iconPath);
        this.active = active;
        this.where = whereQuery;
        this.whereString = serializeWhere(whereQuery);
        this.defaultList = defaultList;
        this.defaultDate = defaultDate;
        setLft(lft);
        setRgt(rgt);
    }

    /**
     * Get all Tasks
     *
     * @return
     */
    @Override
    @NonNull
    public List<Task> tasks() {
        return tasks(false);
    }

    /**
     * Get all Tasks
     *
     * @param showDone
     * @return
     */
    @Override
    @NonNull
    public List<Task> tasks(final boolean showDone) {
        if (showDone) {
            return addSortBy(getWhereQueryForTasks()).getList(Task.class);
        } else {
            return addSortBy(getWhereQueryForTasks().and(Task.DONE, Operation.EQ,
                             false)).getList(Task.class);
        }
    }

    // Static Methods
    public static final String TABLE = "special_lists";
    public static final String WHERE_QUERY = "whereQuery";
    public static final String ACTIVE = "active";
    public static final String DEFAULT_LIST = "def_list";
    public static final String DEFAULT_DUE = "def_date";
    public static final String[] allColumns = {ModelBase.ID,
                                               ModelBase.NAME, WHERE_QUERY, ACTIVE, DEFAULT_LIST,
                                               DEFAULT_DUE, SORT_BY_FIELD, DatabaseHelper.SYNC_STATE_FIELD, COLOR, LFT,
                                               RGT, ICON_PATH
                                              };
    private static final String TAG = "SpecialList";

    private SpecialList create() {
        final long listId = ListMirakel.safeFirst().getId();
        MirakelInternalContentProvider.withTransaction(new
        MirakelInternalContentProvider.DBTransaction() {
            @Override
            public void exec() {
                final ContentValues values = new ContentValues();
                values.put(ModelBase.NAME, getName());
                values.put(WHERE_QUERY, serializeWhere(getWhere()));
                values.put(ACTIVE, active);
                values.put(DEFAULT_LIST, listId);
                setId(insert(URI, values));
                final CursorWrapper c = new MirakelQueryBuilder(context).select("MAX(" + RGT + ')').query(URI);
                c.doWithCursor(new CursorWrapper.WithCursor() {
                    @Override
                    public void withOpenCursor(@NonNull final CursorGetter getter) {
                        getter.moveToFirst();
                        final int maxRGT = getter.getInt(0);
                        final ContentValues cv = new ContentValues();
                        cv.put(LFT, maxRGT + 1);
                        cv.put(RGT, maxRGT + 2);
                        update(URI, cv, ModelBase.ID + '=' + getId(), null);
                    }
                });

            }
        });
        return getSpecial(getId()).get();
    }


    public static SpecialList newSpecialList(final String name,
            final Optional<SpecialListsBaseProperty> whereQuery,
            final boolean active) {
        final SpecialList s = new SpecialList(0L, name, whereQuery, active, Optional.<ListMirakel>absent(),
                                              null, SORT_BY.OPT,
                                              SYNC_STATE.ADD, 0,
                                              0, 0, Optional.<Uri>absent());
        return s.create();
    }

    public static String serializeWhere(
        final Optional<SpecialListsBaseProperty> whereQuery) {
        if (whereQuery.isPresent()) {
            return whereQuery.get().serialize();
        } else {
            return "";
        }
    }

    private static MirakelQueryBuilder packWhere(
        final @NonNull Optional<SpecialListsBaseProperty> where) {

        final MirakelQueryBuilder qb;
        if (where.isPresent()) {
            qb = where.get().getWhereQueryBuilder(context);
        } else {
            qb = new MirakelQueryBuilder(context);
        }
        // Check for account
        Log.d(TAG, "Query:<" + qb.getSelection() + ">");
        return Task.addBasicFiler(qb);
    }

    @Override
    public void save(final boolean log) {
        save();
    }

    /**
     * Update the List in the Database
     */
    @Override
    public void save() {
        setSyncState(((getSyncState() == SYNC_STATE.ADD) ||
                      (getSyncState() == SYNC_STATE.IS_SYNCED)) ? getSyncState() : SYNC_STATE.NEED_SYNC);
        update(URI, getContentValues(),
               ModelBase.ID + " = " + Math.abs(getId()), null);
    }

    /**
     * Delete a List from the Database
     */
    @Override
    public void destroy() {
        MirakelInternalContentProvider.withTransaction(new MirakelInternalContentProvider.DBTransaction() {
            @Override
            public void exec() {
                final long id = Math.abs(getId());
                if (getSyncState() != SYNC_STATE.ADD) {
                    setSyncState(SYNC_STATE.DELETE);
                    setActive(false);
                    final ContentValues values = new ContentValues();
                    values.put(DatabaseHelper.SYNC_STATE_FIELD, getSyncState().toInt());
                    update(URI, values, ModelBase.ID + '=' + id, null);
                } else {
                    delete(URI, ModelBase.ID + '=' + id, null);
                }
                final ContentValues cv = new ContentValues();
                cv.put("TABLE", TABLE);
                update(MirakelInternalContentProvider.UPDATE_LIST_ORDER_URI, cv, LFT + '>' + getLft(), null);
            }
        });
    }

    @NonNull
    @Override
    public ContentValues getContentValues() {
        final ContentValues cv = new ContentValues();
        cv.put(ModelBase.NAME, getName());
        cv.put(SORT_BY_FIELD, getSortBy().getShort());
        cv.put(DatabaseHelper.SYNC_STATE_FIELD, getSyncState().toInt());
        cv.put(ACTIVE, isActive() ? 1 : 0);
        cv.put(WHERE_QUERY, whereString);
        cv.put(DEFAULT_LIST, transformOrNull(this.defaultList, new Function<ListMirakel, Long>() {
            @Override
            public Long apply(ListMirakel input) {
                return input.getId();
            }
        }));
        cv.put(DEFAULT_DUE, this.defaultDate.orNull());
        cv.put(COLOR, getColor());
        cv.put(LFT, getLft());
        cv.put(RGT, getRgt());
        return cv;
    }

    /**
     * Get all SpecialLists
     *
     * @return
     */
    public static List<SpecialList> allSpecial() {
        return allSpecial(false);
    }

    /**
     * Get all SpecialLists
     *
     * @return
     */
    public static List<SpecialList> allSpecial(final boolean showAll) {
        MirakelQueryBuilder qb = new MirakelQueryBuilder(context).and(ACTIVE, Operation.EQ,
                true).sort(LFT, Sorting.ASC);
        if (!showAll) {
            qb.and(ACTIVE, Operation.EQ, true);
        }
        return qb.getList(SpecialList.class);
    }

    /**
     * Get a List by id
     *
     * @param listId List–ID
     * @return List
     */
    public static Optional<SpecialList> getSpecial(final long listId) {
        return new MirakelQueryBuilder(context).get(SpecialList.class, Math.abs(listId));
    }

    /**
     * Get the first List
     *
     * @return List
     */
    public static Optional<SpecialList> firstSpecial() {
        return new MirakelQueryBuilder(context).and(DatabaseHelper.SYNC_STATE_FIELD,
                Operation.NOT_EQ, SYNC_STATE.DELETE.toInt()).sort(LFT,
                        Sorting.ASC).get(SpecialList.class);
    }

    public static SpecialList firstSpecialSafe() {
        Optional<SpecialList> s = SpecialList.firstSpecial();
        if (!s.isPresent()) {
            s = fromNullable(SpecialList.newSpecialList(context.getString(R.string.list_all),
                             Optional.<SpecialListsBaseProperty>absent(), true));
            if (ListMirakel.count() == 0) {
                ListMirakel.safeFirst();
            }
            s.get().save(false);
        }
        return s.get();
    }

    private static AccountMirakel getAccountFromCursor(final CursorGetter cursor) {
        final int columnIndex = cursor.getColumnIndex(ACCOUNT_ID);
        if (columnIndex >= 0) {
            return AccountMirakel.get(cursor.getInt(columnIndex)).or(AccountMirakel.getLocal());
        }
        return AccountMirakel.getLocal();
    }

    /**
     * Create a List from a Cursor
     *
     * @param c
     */
    public SpecialList(final @NonNull CursorGetter c) {
        super(c.getLong(ID), c.getString(NAME),
              SORT_BY.fromShort(c.getShort(SORT_BY_FIELD)), "", "",
              SYNC_STATE.valueOf(c.getShort(DatabaseHelper.SYNC_STATE_FIELD)),
              c.getInt(LFT), c.getInt(RGT), c.getInt(COLOR),
              getAccountFromCursor(c), FileUtils.parsePath(c.getString(ICON_PATH)));
        this.whereString = c.getString(WHERE_QUERY);
        this.active = c.getBoolean(ACTIVE);
        this.defaultList = ListMirakel.get(c.getLong(DEFAULT_LIST));
        this.defaultDate = c.getOptional(DEFAULT_DUE, Integer.class);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof SpecialList)) {
            return false;
        }
        SpecialList other = (SpecialList) obj;
        if (other.active != this.active) {
            return false;
        }
        if (!Objects.equal(other.whereString, this.whereString)) {
            return false;
        }
        if (!Objects.equal(other.defaultDate, this.defaultDate)) {
            return false;
        }
        if (!Objects.equal(other.defaultList, this.defaultList)) {
            return false;
        }
        return true;
    }

    // Parcelable stuff

    @Override
    public int describeContents() {
        return 0;
    }
    public static final int NULL_DATE_VALUE = -1337;

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeByte(active ? (byte) 1 : (byte) 0);
        dest.writeParcelable(this.defaultList.orNull(), 0);
        dest.writeInt(this.defaultDate.or(NULL_DATE_VALUE));
        dest.writeString(this.whereString);
        dest.writeInt((this.sortBy == null) ? -1 : this.sortBy.ordinal());
        dest.writeString(this.createdAt);
        dest.writeString(this.updatedAt);
        dest.writeInt((this.syncState == null) ? -1 : this.syncState.ordinal());
        dest.writeInt(this.lft);
        dest.writeInt(this.rgt);
        dest.writeInt(this.color);
        dest.writeLong(this.accountID);
        dest.writeString(getIconPathString());
        dest.writeLong(this.getId());
        dest.writeString(this.getName());
    }

    private SpecialList(Parcel in) {
        this.active = in.readByte() != 0;
        this.defaultList = fromNullable((ListMirakel) in.readParcelable(
                                            ListMirakel.class.getClassLoader()));
        final int tmpDefaultDate = in.readInt();
        this.defaultDate = (tmpDefaultDate == NULL_DATE_VALUE) ? Optional.<Integer>absent() : Optional.of(
                               tmpDefaultDate);
        this.whereString = in.readString();
        final int tmpSortBy = in.readInt();
        this.sortBy = (tmpSortBy == -1) ? null : SORT_BY.values()[tmpSortBy];
        this.createdAt = in.readString();
        this.updatedAt = in.readString();
        final int tmpSyncState = in.readInt();
        this.syncState = (tmpSyncState == -1) ? null : SYNC_STATE.values()[tmpSyncState];
        this.lft = in.readInt();
        this.rgt = in.readInt();
        this.color = in.readInt();
        this.accountID = in.readLong();
        this.setIconPath(FileUtils.parsePath(in.readString()));
        this.setId(in.readLong());
        this.setName(in.readString());
    }

    public static final Creator<SpecialList> CREATOR = new Creator<SpecialList>() {
        public SpecialList createFromParcel(final Parcel source) {
            return new SpecialList(source);
        }

        public SpecialList[] newArray(final int size) {
            return new SpecialList[size];
        }
    };
}
