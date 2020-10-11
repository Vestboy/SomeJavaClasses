package com.enlearner.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.enlearner.BaseActivity;
import com.enlearner.MyApplication;
import com.enlearner.R;
import com.enlearner.activities.main_activity.AuxClasses.MainActivity.MainActivityAdditionalMethodsFragments;
import com.enlearner.activities.show_word_activity.ShowWordActivity;
import com.enlearner.common.CommonConsts;
import com.enlearner.common.CommonMethods;
import com.enlearner.common.LogWriter;
import com.enlearner.common.ThemesAndColorsHelper;
import com.enlearner.common.transitions.TransitionUtils;
import com.enlearner.import_export.FileExportDialog;
import com.enlearner.sql_helper.main_db.HistorySQLAdapter;

import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import objects.Word;

public class HistoryActivity extends BaseActivity {

    static final String TAG = HistoryActivity.class.getName();
    static final int REQUEST_CODE_EXPORT = 1368;

    HistorySQLAdapter historyAdapter;

    AppCompatButton clearHistoryButton;
    AppCompatButton exportToXLSButton;
    Button goToManualButton;

    ListView lv;
    Parcelable state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogWriter.WriteToLog(TAG + " onCreate()");
        setTheme(MyApplication.getThemeId());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ThemesAndColorsHelper.SetActivityStyleAttributes(this, false);

        setUpView();

    }

    private void setUpView() {

        LogWriter.WriteToLog(TAG + " setUpView()");

        clearHistoryButton = (AppCompatButton) findViewById(R.id.clearHistoryButton);
        exportToXLSButton = (AppCompatButton) findViewById(R.id.exportToXLSButton);

        goToManualButton =
                ThemesAndColorsHelper.StyleAndGetGoToManualButtonAndSetHeader(
                        this,
                        getString(R.string.history),
                        true);

        setButtonsListeners();

        lv = (ListView) findViewById(R.id.listView);

        lv.setOnItemClickListener((arg0, arg1, position, arg3) -> {
            Log.v(TAG, "onListItemClick before getItem");

            Word selectedName = historyAdapter.getItem(position);

            Intent goToShowWordForm = new Intent(
                    HistoryActivity.this,
                    ShowWordActivity.class);

            goToShowWordForm.putExtra("chosenWord", selectedName);
            goToShowWordForm.putExtra("wordSource", CommonConsts.ShowWordActivityWordSource.FROM_MAIN_LIST.toString());
            goToShowWordForm.putExtra("isFromHistory", true);

            View textView = arg1.findViewById(R.id.activity_main_list_item_text);
            textView.setTransitionName("mainActivityWordsListWord");

            List<Pair<View, String>> transitionPairs = TransitionUtils.GetDefaultSharedElements(HistoryActivity.this);
            transitionPairs.add(Pair.create(textView, textView.getTransitionName()));
            View topColoredStrip = HistoryActivity.this.findViewById(R.id.history_activity_fake_top_colored_strip);
            if (topColoredStrip != null) {
                transitionPairs.add(Pair.create(topColoredStrip, topColoredStrip.getTransitionName()));
            }

            ActivityOptionsCompat activityOptionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(HistoryActivity.this, transitionPairs.toArray(new Pair[transitionPairs.size()]));

            startActivity(goToShowWordForm, activityOptionsCompat.toBundle());
        });

        historyAdapter = new HistorySQLAdapter(this);
        historyAdapter.open(TAG + " setUpView()");
        lv.setAdapter(historyAdapter);

    }

    void setButtonsListeners() {
        LogWriter.WriteToLog(TAG + " setButtonsListeners()");
        clearHistoryButton.setOnClickListener(v -> clearHistory());
        exportToXLSButton.setOnClickListener(v -> exportToFile());
        goToManualButton.setOnClickListener(v -> goToManual());
    }

    private void clearHistory() {
        LogWriter.WriteToLog(TAG + " clearHistory()");

        if (historyAdapter.getCount() != 0) {
            AlertDialog.Builder ad;
            String title = getString(R.string.are_you_sure_you_want_to_clear_the_history) + "?";

            String button1String = getString(R.string.yes);
            String button2String = getString(R.string.no);

            ad = new AlertDialog.Builder(HistoryActivity.this);

            ad.setMessage(title);

            ad.setPositiveButton(
                    button1String, (dialog, arg1) -> {

                        historyAdapter.clearTable();
                        historyAdapter.notifyDataSetChanged();

                    });

            ad.setNegativeButton(
                    button2String, (dialog, arg1) -> {

                    });

            AlertDialog dialog1 = ad.show();

            TextView messageView1 = (TextView) dialog1.findViewById(android.R.id.message);
            messageView1.setGravity(Gravity.CENTER);
            messageView1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);


        } else
            Toast.makeText(
                    getBaseContext(),
                    getResources().getString(R.string.history_is_empty),
                    Toast.LENGTH_SHORT)
                    .show();

    }

    private void exportToFile() {

        LogWriter.WriteToLog(TAG + " exportToFile()");

        if (!MainActivityAdditionalMethodsFragments.IsExternalStorageWritable()) {
            Toast.makeText(HistoryActivity.this,
                    getResources().getString(R.string.storage_write_not_granted), Toast.LENGTH_LONG).show();
            return;
        }

        if (historyAdapter.getCount() != 0) {

            Intent goToExportDialog = new Intent(
                    HistoryActivity.this,
                    FileExportDialog.class);

            goToExportDialog.putExtra("curUserDictName", HistorySQLAdapter.HISTORY_TABLE_NAME);
            goToExportDialog.putExtra("isUserDict", false);

            startActivityForResult(goToExportDialog, REQUEST_CODE_EXPORT);

        } else
            Toast.makeText(
                    getBaseContext(),
                    getResources().getString(R.string.history_is_empty),
                    Toast.LENGTH_SHORT)
                    .show();

    }

    private void goToManual() {
        LogWriter.WriteToLog(TAG + " goToManual()");
        Intent goToManual = new Intent(this, ManualActivity.class);

        goToManual.putExtra("calling_activity", "history_activity");

        this.startActivity(goToManual);

    }

    @Override
    protected void onPause() {
        LogWriter.WriteToLog(TAG + " onPause()");

        state = lv.onSaveInstanceState();

        super.onPause();

    }

    @Override
    protected void onResume() {
        LogWriter.WriteToLog(TAG + " onResume()");

        super.onResume();

        if (state != null)
            lv.onRestoreInstanceState(state);
    }

    @Override
    public void onDestroy() {
        LogWriter.WriteToLog(TAG + " onDestroy()");
        super.onDestroy();

        if (historyAdapter != null) {
            historyAdapter.close(TAG + " onDestroy()");
        }

        lv.setAdapter(null);

        CommonMethods.UnbindDrawables(findViewById(R.id.mainView));

    }

    @Override
    public void onBackPressed() {
        LogWriter.WriteToLog(TAG + " onBackPressed()");

        super.onBackPressed();

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_EXPORT:

                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        final String message = data.getStringExtra("message");

                    } else {
                        Toast.makeText(HistoryActivity.this, getString(R.string.error), Toast.LENGTH_LONG).show();
                    }
                }

                break;
        }
        super.onActivityResult(requestCode, resultCode, data);

    }
}
