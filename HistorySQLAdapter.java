package com.enlearner.sql_helper.main_db;


import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.enlearner.MyApplication;
import com.enlearner.R;
import com.enlearner.common.CommonVariables;
import com.enlearner.common.LogWriter;
import com.enlearner.preferences.DefSharedPrefsManager;

import java.util.Objects;

import objects.UserDictionary;
import objects.Word;

public final class HistorySQLAdapter extends ArrayAdapter<Object> implements ISQLAdapter {

    //region FIELDS AND VARIABLES

    private static final String TAG = HistorySQLAdapter.class.getName();
    public static final String HISTORY_TABLE_NAME = "history";

    public static final String KEY_ID = "_id";
    private static final int ID_COLUMN = 0;

    public static final String KEY_NAME = "name";
    private static final int NAME_COLUMN = 1;

    static final String KEY_TRANS_DIRECTION = "transDirection";
    private static final int TRANS_DIRECTION_COLUMN = 2;

    static final String WORD_TRANSCRIPTION = "transcription";
    private static final int TRANSCRIPTION_COLUMN = 3;

    static final String WORD_TRANSLATE = "translation";
    private static final int TRANSLATE_COLUMN = 4;

    static final String WORD_SHORT_TRANSLATION = "shorttranslation";
    private static final int SHORT_TRANSLATION_COLUMN = 5;

    static final String WORD_EXAMPLE = "example";
    private static final int WORD_EXAMPLE_COLUMN = 6;

    static final String WORD_DICT = "dictionary";
    private static final int DICT_COLUMN = 7;

    static final String KEY_LINENUMBER = "linenumber";
    private static final int LINENUMBER_COLUMN = 8;

    static final String IMAGE_LINK = "image_link";
    private static final int IMAGE_LINK_COLUMN = 9;

    static final String LEARNING_POINTS = "learningPoints";
    private static final int LEARNING_POINTS_COLUMN = 10;

    static final String LAST_REPEAT_TIME = "nextRepeatTime";
    private static final int LAST_REPEAT_TIME_COLUMN = 11;

    static final String POINTS_IN_CUR_SESSION = "pointsInCurSession";
    private static final int POINTS_IN_CUR_SESSION_COLUMN = 12;

    static final String WRONG_ANSWERS = "wrongAnswers";
    private static final int WRONG_ANSWERS_COLUMN = 13;

    private Cursor cursor;

    private SQLiteDatabase database;

    private Context context;
    private long id;

    //endregion

    public HistorySQLAdapter(Context context) {
        super(context, 0);
        this.context = context;
    }

    public String getCurrentDictName() {
        UserDictsListSQLAdapter userDictListAdapter = new UserDictsListSQLAdapter(context);
        UserDictionary userDict = userDictListAdapter.getCurrentUserDict();
        String currentDictName;
        if (userDict != null) {
            currentDictName = userDict.getDictName();
        } else currentDictName = null;
        Log.v(TAG, "curUserDictName = " + currentDictName);
        return currentDictName;
    }

    public HistorySQLAdapter open(String _callingMethod) {
        LogWriter.WriteToLog(TAG + " open() called by: " + _callingMethod);

        DatabaseManager.initializeInstance(context.getApplicationContext());
        this.database = DatabaseManager.getInstance().openDatabase();
        cursor = getAllEntries();
        return this;
    }

    public void close(String _callingMethod) {
        LogWriter.WriteToLog(TAG + " close() called by: " + _callingMethod);
        if (cursor == null)
            LogWriter.WriteToLog(TAG + " close() cursor == null");
        if (cursor != null && !cursor.isClosed())
            cursor.close();
    }

    @Override
    public long getItemId(int position) {
        Word nameOnPosition = getItem(position);
        return nameOnPosition.getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LinearLayout layout;
        if (null == convertView) {
            layout = (LinearLayout) View.inflate(context, R.layout.activity_main_list_item,
                    null);
            TextView textView1;
            textView1 = layout.findViewById(R.id.activity_main_list_item_text);
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance());
            int fontSize = Integer.parseInt(Objects.requireNonNull(shared.getString(DefSharedPrefsManager.mainListFontKey, "18")));
            textView1.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
            textView1.setTextColor(CommonVariables.GetMyColorStateList());

        } else {
            layout = (LinearLayout) convertView;
        }

        TextView textView1 = layout.findViewById(R.id.activity_main_list_item_text);
        textView1.setText(getItem(position).getName());

        return layout;
    }

    @Override
    public int getCount() {
        return cursor.getCount();
    }

    @Override
    public Word getItem(int position) {
        if (cursor.isClosed()) open(TAG + " getItem()");
        if (cursor.moveToPosition(position)) {
            long id = cursor.getLong(ID_COLUMN);
            String name = cursor.getString(NAME_COLUMN);
            String transDirection = cursor.getString(TRANS_DIRECTION_COLUMN);
            String wordTranscription = cursor.getString(TRANSCRIPTION_COLUMN);
            String wordTranslate = cursor.getString(TRANSLATE_COLUMN);
            String wordShortTranslation = cursor.getString(SHORT_TRANSLATION_COLUMN);
            String wordExample = cursor.getString(WORD_EXAMPLE_COLUMN);
            String wordDict = cursor.getString(DICT_COLUMN);
            int lineNumber = cursor.getInt(LINENUMBER_COLUMN);
            String imageLink = cursor.getString(IMAGE_LINK_COLUMN);
            int learningPoints = cursor.getInt(LEARNING_POINTS_COLUMN);
            int lastRepeatTime = cursor.getInt(LAST_REPEAT_TIME_COLUMN);
            int pointsInCurSession = cursor.getInt(POINTS_IN_CUR_SESSION_COLUMN);
            int wrongAnswers = cursor.getInt(WRONG_ANSWERS_COLUMN);
            int isChecked = 0;
            int nextRepeatTime = cursor.getInt(LAST_REPEAT_TIME_COLUMN);

            Word nameOnPositon =
                    new Word(
                            id,
                            name,
                            transDirection,
                            wordTranscription,
                            wordTranslate,
                            wordShortTranslation,
                            wordExample,
                            wordDict,
                            lineNumber,
                            imageLink,
                            learningPoints,
                            lastRepeatTime,
                            pointsInCurSession,
                            wrongAnswers,
                            isChecked,
                            nextRepeatTime,
                            null,
                            null);

            return nameOnPositon;
        } else {
            throw new CursorIndexOutOfBoundsException(
                    "Cant move cursor to position");
        }
    }


    public Cursor getAllEntries() {
        Cursor cursor;

        String[] columnsToTake = {
                KEY_ID,
                KEY_NAME,
                KEY_TRANS_DIRECTION,
                WORD_TRANSCRIPTION,
                WORD_TRANSLATE,
                WORD_SHORT_TRANSLATION,
                WORD_EXAMPLE,
                WORD_DICT,
                KEY_LINENUMBER,
                IMAGE_LINK,
                LEARNING_POINTS,
                LAST_REPEAT_TIME,
                POINTS_IN_CUR_SESSION,
                WRONG_ANSWERS};


        cursor = database.query(HISTORY_TABLE_NAME, columnsToTake,
                null, null, null, null, KEY_ID);

        return cursor;

    }

    public long addItemToTable(Word name) {

        final ContentValues values = new ContentValues();
        values.put(KEY_NAME, name.getName());

        values.put(KEY_TRANS_DIRECTION, name.getTransDirection());

        String trans = name.getWordTranscription();
        if (trans != null && !trans.equals(""))
            values.put(WORD_TRANSCRIPTION, trans);
        else
            values.put(WORD_TRANSCRIPTION, "");

        String translation = name.getWordTranslate();
        if (translation != null && !translation.equals(""))
            values.put(WORD_TRANSLATE, translation);
        else
            values.put(WORD_TRANSLATE, "");

        String shortTranslation = name.getWordShortTranslation();
        if (shortTranslation != null && !shortTranslation.equals(""))
            values.put(WORD_SHORT_TRANSLATION, shortTranslation);
        else
            values.put(WORD_SHORT_TRANSLATION, "");

        String example = name.getWordExample();
        if (example != null && !example.equals(""))
            values.put(WORD_EXAMPLE, example);

        String wordDictionary = name.getDict();
        if (wordDictionary != null && !wordDictionary.equals(""))
            values.put(WORD_DICT, wordDictionary);

        values.put(KEY_LINENUMBER, name.getLineNumber());

        String imageLink = name.getWordImageLink();
        if (imageLink != null && !imageLink.equals(""))
            values.put(IMAGE_LINK, imageLink);

        values.put(LEARNING_POINTS, name.getWordPoints());
        values.put(LAST_REPEAT_TIME, name.getLastRepeatTime());
        values.put(POINTS_IN_CUR_SESSION, name.getPointsInCurSession());
        values.put(WRONG_ANSWERS, name.getWrongAnswers());

        try {
            id = database.insertWithOnConflict(HISTORY_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLiteConstraintException e) {
            LogWriter.WriteToLog("EXC " + TAG + "addItemToTable() " + e.getMessage(), e);
        }

        refresh();

        return id;
    }

    public void clearTable() {
        database.execSQL("DELETE FROM " + HISTORY_TABLE_NAME);
        refresh();
    }

    public void refresh() {
        cursor = getAllEntries();
        notifyDataSetChanged();
    }

}
