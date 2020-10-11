package com.enlearner.activities.show_word_activity;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.enlearner.DictionaryHelper;
import com.enlearner.MyApplication;
import com.enlearner.R;
import com.enlearner.activities.ManualActivity;
import com.enlearner.activities.RecordWordActivity;
import com.enlearner.common.CommonConsts;
import com.enlearner.common.CommonMethods;
import com.enlearner.common.CommonVariables;
import com.enlearner.common.LogWriter;
import com.enlearner.common.SayManager;
import com.enlearner.common.ThemesAndColorsHelper;
import com.enlearner.common.transitions.EnterSharedElementTextSizeHandler;
import com.enlearner.common.transitions.TransitionUtils;
import com.enlearner.learner.ExercisesHelper;
//import com.enlearner.online_services.GetPronunciationFromForvoAT;
import com.enlearner.online_services.GetTranslationFromOnlineAT;
import com.enlearner.preferences.DefSharedPrefsManager;
import com.enlearner.source_dictionaries.ISourceDict;
import com.enlearner.source_dictionaries.SourceDictsFactory;
import com.enlearner.sql_helper.main_db.DictsListSQLAdapter;
import com.r0adkll.slidr.Slidr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import objects.CommonResult;
import objects.Dictionary;
import objects.Word;
import services.BaseActivitySpeechServiceClient;
import services.MySpeechService;

public class ShowWordActivity extends BaseActivitySpeechServiceClient {

    //region VARIABLES

    static final String TAG = ShowWordActivity.class.getName();

    final boolean IsShowTranscription = true;

    public TextView wordField;
    public TextView transcriptionField;
    public TextView notFoundTextView;

    public AppCompatImageButton searchWordButton;

    public ProgressBar progressBar;
    public TextView yandexLinkTextView;

    public WebView translationWebView;

    ImageButton sayButton;
    ImageButton learnButton;
    ImageButton moreActionsButton;
    Button goToManualButton;

    AppCompatImageButton scrollUpButton;
    AppCompatImageButton scrollDownButton;

    AppCompatImageButton scrollDownDefButton;
    AppCompatImageButton scrollUpDefButton;

    EditText searchWordInputField;
    RelativeLayout searchWordPanelLayout;

    public String wordName = "";

    public String newTranscription = "";
    public String shortTranslation = "";
    public String wordExample = "";

    public String transDirection;

    public StringBuilder[] wordTranslationArray;

    public static int exampleCounter;
    public static int shortTranslationCounter;

    public boolean inputFieldIsShown = false;

    public int webViewLoadedTranslationsCount = 0;

    public boolean mHasToRestoreState = false;
    public float mProgressToRestore;

    public int currentDictPosition;

    int strNum;
    String dict = "";
    String imageFilePath = "";

    boolean isFromOnline = false;
    boolean isFromWidget = false;

    GetTranslationFromOnlineAT getTranslationFromOnlineAT;

    ArrayList<ISourceDict> dictsToSearch;

    int screenHeight;

    StringBuilder contentToPutToWebView;

    int transFontSize;
    int wordFontSize;

    private SpeechEndReceiver mSpeechEndReceiver;

    Handler delayedStopSpeechHandler;

    boolean isExamplesShown = true;

    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setTheme(MyApplication.getThemeId());

        super.onCreate(savedInstanceState);

        getWindow().setAllowEnterTransitionOverlap(true);
        getWindow().setEnterTransition(TransitionUtils.makeEnterTransition());

        setContentView(R.layout.activity_show_word);

        Slidr.attach(this);

        ThemesAndColorsHelper.SetActivityStyleAttributes(this, false);

        goToManualButton =
                ThemesAndColorsHelper.StyleAndGetGoToManualButtonAndSetHeader(
                        this,
                        "",
                        true);

        ShowWordActivityDoExtraThingsAT doExtraThingsAT = new ShowWordActivityDoExtraThingsAT(this);
        doExtraThingsAT.execute();

    }

    final static class OrientationChangeData {
        float mProgress;
        StringBuilder[] translationsArray;
        String transcript;
        int dictPosition;
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        OrientationChangeData objectToSave = new OrientationChangeData();
        objectToSave.mProgress = calculateProgression(translationWebView);
        objectToSave.translationsArray = wordTranslationArray;
        objectToSave.transcript = newTranscription;

        return objectToSave;
    }

    private float calculateProgression(WebView content) {
        if (content == null) {
            return 0;
        }
        float positionTopView = content.getTop();
        float contentHeight = content.getContentHeight();
        float currentScrollPosition = content.getScrollY();
        float percentWebview = (currentScrollPosition - positionTopView) / contentHeight;
        return percentWebview;
    }

    public void setDataToActivity(int translationsArrayPosition) {

        if (IsShowTranscription) {
            if (CommonVariables.TransDirection.equals(CommonMethods.GetCurFullTransDirection(CommonConsts.TO_NATIVE_TRANS_DIRECTION_NAME_SHORT))
                    || isFromWidget) {
                if (!newTranscription.equals("")) {
                    if (transcriptionField.getText().equals("")) {
                        runOnUiThread(() -> {
                            Handler handler = new Handler();
                            handler.postDelayed(() -> {
                                transcriptionField.setVisibility(View.VISIBLE);
                                transcriptionField
                                        .setText(newTranscription);
                            }, 500);
                        });
                    }
                }
            }
        }

        putDataThemedToWebView(
                translationWebView);

        webViewLoadedTranslationsCount++;

    }

    public boolean handleUri(final Uri uri) {

        String wordToShow = "";
        String scheme = uri.getScheme();
        if (scheme.equals("file")) {
            wordToShow = uri.getLastPathSegment();
        }
        Intent goToShowWordForm = new Intent(ShowWordActivity.this, ShowWordActivity.class);
        Word selectedName = new Word();
        selectedName.setName(wordToShow);
        selectedName.setTransDirection(transDirection);

        goToShowWordForm.putExtra("chosenWord", selectedName);

        goToShowWordForm.putExtra("wordSource", CommonConsts.ShowWordActivityWordSource.FROM_MAIN_LIST.toString());

        List<Pair<View, String>> transitionPairs = TransitionUtils.GetDefaultSharedElements(ShowWordActivity.this);
//        transitionPairs.add(Pair.create(textView, textView.getTransitionName()));
        View topColoredStrip = ShowWordActivity.this.findViewById(R.id.history_activity_fake_top_colored_strip);
        if (topColoredStrip != null) {
            transitionPairs.add(Pair.create(topColoredStrip, topColoredStrip.getTransitionName()));
        }

        ActivityOptionsCompat activityOptionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(ShowWordActivity.this, transitionPairs.toArray(new Pair[transitionPairs.size()]));

        startActivity(goToShowWordForm, activityOptionsCompat.toBundle());

        return true;

    }

    public void putDataThemedToWebView(WebView _webView) {

        if (webViewLoadedTranslationsCount == 0
                || wordTranslationArray.length == 0) {
            int curTheme = MyApplication.getThemeId();
            String styleSheetName;
            int mainColorInt;
            int secondColorInt;
            if (curTheme == R.style.CUSTOM_THEME_LIGHT
                    || curTheme == R.style.CUSTOM_COLOR_THEME_LIGHT
                    || curTheme == R.style.CUSTOM_COLOR_THEME_LIGHT_INVERTED_IMAGES) {
                mainColorInt =
                        PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance()).getInt("translationMainTextColorForLight", 0);
                secondColorInt =
                        PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance()).getInt("translationSecondTextColorForLight", 0);

                styleSheetName = "styles_for_light.css";
            } else {
                mainColorInt =
                        PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance()).getInt("translationMainTextColorForDark", 0);
                secondColorInt =
                        PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance()).getInt("translationSecondTextColorForDark", 0);

                styleSheetName = "styles_for_dark.css";
            }
            String hexMainColor = String.format("#%06X", (0xFFFFFF & mainColorInt));
            String hexSecondColor = String.format("#%06X", (0xFFFFFF & secondColorInt));
            String hexSecondColorHighlighted = String.format("#%06X", (0xFFFFFF & ThemesAndColorsHelper.GetHighlightColor(secondColorInt, 0.2f)));

            contentToPutToWebView.append("<HTML>" + "<HEAD>" + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                    + "<style>"
                    + "body {" + "color: ").append(hexMainColor).append("; ")
                    .append("font-size: ").append(transFontSize).append("px;").append("}")
                    .append(".small    {color: ").append(hexSecondColor).append("; font-size: 80%;}")
                    .append(".container0    {padding-left: 0px;}")
                    .append(".container1    {padding-left: 10px;}")
                    .append(".container2    {padding-left: 20px;}")
                    .append(".container3    {padding-left: 30px;}")
                    .append(".container4    {padding-left: 40px;}")
                    .append(".example    {color: ").append(hexSecondColor).append("; font-size: 95%;}")
                    .append(".dictionary-name    {color: ").append(hexSecondColorHighlighted).append("; font-size: 120%; font-family: 'Times New Roman', Times, serif; text-align: right; padding-top: 10px; padding-bottom: 10px;}")
                    .append("</style>")
                    .append("<LINK href=\"").append(styleSheetName).append("\" type=\"text/css\" rel=\"stylesheet\"/>")
                    .append("<script>")
                    .append("function scrollAnchor(id) {")
//                    .append("window.location.hash = id;")
                    .append("document.getElementsByName(id)[0].scrollIntoView();")
//                    .append("alert(\"sdff\");")
                    .append("}")
                    .append("</script>")
                    .append("</HEAD><body>");

        }

        if (webViewLoadedTranslationsCount == wordTranslationArray.length - 1) {

            for (StringBuilder data : wordTranslationArray)
                contentToPutToWebView.append(data);

            contentToPutToWebView.append("</body>");

            contentToPutToWebView.append("</HTML>");
            long curTime = System.currentTimeMillis();
            runOnUiThread(() -> {
                _webView.loadDataWithBaseURL("file:///android_asset/", contentToPutToWebView.toString(), "text/html", "utf-8", null);

                Log.v(TAG, "WebView load data time = " + String.valueOf(System.currentTimeMillis() - curTime));

                //here is a last call from getWordArticleAT
                progressBar.setVisibility(View.GONE);

                //show not found tv if all translations are empty
                final Handler handler = new Handler();
                Runnable runnable = () -> {
                    boolean isTranslationFound = false;
                    try {
                        Thread.sleep(100);

                        for (StringBuilder tr : wordTranslationArray) {
                            if (tr.length() != 0) {
                                isTranslationFound = true;
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (!isTranslationFound) {
                        handler.post(() -> {
                            notFoundTextView.setVisibility(View.VISIBLE);
                            searchWordButton.setEnabled(false);
                        });
                    }
                };
                new Thread(runnable).run();
            });
        }
    }

    private void hideShowExamples(boolean isShow) {
        isExamplesShown = isShow;
        if (isShow) {
            String newContentToPutToWebView = CommonMethods.replaceInString(contentToPutToWebView.toString(), "<div style=\"display: none\" class=\"container1\"><div class=\"example\">", "<div class=\"container1\"><div class=\"example\">");
            newContentToPutToWebView = CommonMethods.replaceInString(newContentToPutToWebView, "<div style=\"display: none\" class=\"container2\"><div class=\"example\">", "<div class=\"container2\"><div class=\"example\">");
            newContentToPutToWebView = CommonMethods.replaceInString(newContentToPutToWebView, "<div style=\"display: none\" class=\"container3\"><div class=\"example\">", "<div class=\"container3\"><div class=\"example\">");
            newContentToPutToWebView = CommonMethods.replaceInString(newContentToPutToWebView, "<div style=\"display: none\" class=\"example\">", "<div class=\"example\">");
            translationWebView.loadDataWithBaseURL("file:///android_asset/", newContentToPutToWebView, "text/html", "utf-8", null);
        } else {
            String newContentToPutToWebView = CommonMethods.replaceInString(contentToPutToWebView.toString(), "<div class=\"container1\"><div class=\"example\">", "<div style=\"display: none\" class=\"container1\"><div class=\"example\">");
            newContentToPutToWebView = CommonMethods.replaceInString(newContentToPutToWebView, "<div class=\"container2\"><div class=\"example\">", "<div style=\"display: none\" class=\"container2\"><div class=\"example\">");
            newContentToPutToWebView = CommonMethods.replaceInString(newContentToPutToWebView, "<div class=\"container3\"><div class=\"example\">", "<div style=\"display: none\" class=\"container3\"><div class=\"example\">");
            newContentToPutToWebView = CommonMethods.replaceInString(newContentToPutToWebView, "<div class=\"example\">", "<div style=\"display: none\" class=\"example\">");
            translationWebView.loadDataWithBaseURL("file:///android_asset/", newContentToPutToWebView, "text/html", "utf-8", null);
        }
    }

    private void showRecordForm() {

        Intent goToRecordWordForm = new Intent(
                ShowWordActivity.this,
                RecordWordActivity.class);

        Word wordToRecord = new Word();

        wordToRecord.setName(DictionaryHelper.NormalizeWord(wordName));
        wordToRecord.setTransDirection(transDirection);
        wordToRecord.setTranscription(newTranscription);
        StringBuilder stringBuilder = new StringBuilder();
        for (StringBuilder tr : wordTranslationArray) {
            stringBuilder.append(tr);
        }
        wordToRecord.setTranslation(stringBuilder.toString());
        wordToRecord.setWordShortTranslation(shortTranslation);
        wordToRecord.setExample(wordExample);
        wordToRecord.setDict(dict);
        wordToRecord.setPositionInDict(strNum);
        wordToRecord.setImage(imageFilePath);

        goToRecordWordForm.putExtra("wholeWord", wordToRecord);


        goToRecordWordForm.putExtra("wordSource", RecordWordActivity.RecordWordActivityWordSource.FROM_SHOW_WORD_ACTIVITY.toString());

        View textView = findViewById(R.id.show_word_wordField);
        textView.setTransitionName("mainActivityWordsListWord");

        List<Pair<View, String>> transitionPairs = TransitionUtils.GetDefaultSharedElements(ShowWordActivity.this);
        transitionPairs.add(Pair.create(textView, textView.getTransitionName()));

        ActivityOptionsCompat activityOptionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(ShowWordActivity.this, transitionPairs.toArray(new Pair[transitionPairs.size()]));

        startActivity(goToRecordWordForm, activityOptionsCompat.toBundle());

    }

    @SuppressLint("RestrictedApi")
    void setButtonsListeners() {
        learnButton.setOnClickListener(v -> showRecordForm());
        moreActionsButton.setOnClickListener(v -> {

            PopupMenu menu = new PopupMenu(ShowWordActivity.this, moreActionsButton);
            menu.inflate(R.menu.show_word_activity_actions_menu);
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case R.id.show_word_activity_actions_menu_show_examples:
                        hideShowExamples(!isExamplesShown);
                        break;
                }
                return false;
            });

            @SuppressLint("RestrictedApi") MenuPopupHelper menuHelper = new MenuPopupHelper(ShowWordActivity.this, (MenuBuilder) menu.getMenu(), v);
            menuHelper.setForceShowIcon(true);
            menuHelper.show();
        });
        sayButton.setOnClickListener(v -> {

            mSpeechEndReceiver = new SpeechEndReceiver();
            IntentFilter filter = IntentFilter.create(MySpeechService.SPEECH_END_INTENT_ACTION, "text/plain");
//                filter.addAction(SayManager.LANGUAGE_NOT_SUPPORTED_ACTION);
            LocalBroadcastManager.getInstance(ShowWordActivity.this).registerReceiver(mSpeechEndReceiver, filter);

            ShowProgressBar();

            String wordLanguage = transDirection.substring(0, 2);
            MyApplication.getInstance().Speak(wordName, wordLanguage);

            delayedStopSpeechHandler = SayManager.SendDelayedSpeechFinishedBroadcast(ShowWordActivity.this);
        });

        goToManualButton.setOnClickListener(v -> goToManual());

        searchWordButton.setOnClickListener(v -> {

            int themeId = MyApplication.getThemeId();

            if (searchWordPanelLayout.getTag() == null || (int) searchWordPanelLayout.getTag() == 0) {
                searchWordPanelLayout.setVisibility(View.VISIBLE);

                searchWordPanelLayout.setTag(1);

                searchWordPanelLayout.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int height = searchWordPanelLayout.getMeasuredHeight();

                ValueAnimator slideAnimator = ValueAnimator
                        .ofInt(0, height)
                        .setDuration(300);

                slideAnimator.addUpdateListener(animation -> {
                    Integer value = (Integer) animation.getAnimatedValue();
                    searchWordPanelLayout.getLayoutParams().height = value.intValue();
                    searchWordPanelLayout.requestLayout();
                });

                AnimatorSet set = new AnimatorSet();
                set.playTogether(slideAnimator);
                set.setInterpolator(new AccelerateDecelerateInterpolator());
                set.start();

                searchWordInputField.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(searchWordInputField, InputMethodManager.SHOW_IMPLICIT);
                inputFieldIsShown = true;

                if (themeId == R.style.CUSTOM_COLOR_THEME_LIGHT) {
                    searchWordButton.setImageResource(R.drawable.light_main_activity_search_with_up_arrow_button_image);
                } else if (themeId == R.style.CUSTOM_COLOR_THEME_DARK) {
                    searchWordButton.setImageResource(R.drawable.dark_main_activity_search_with_up_arrow_button_image);
                }

            } else {

                searchWordPanelLayout.setTag(0);

                searchWordPanelLayout.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int height = searchWordPanelLayout.getMeasuredHeight();

                ValueAnimator slideAnimator = ValueAnimator
                        .ofInt(height, 0)
                        .setDuration(300);

                slideAnimator.addUpdateListener(animation -> {
                    Integer value = (Integer) animation.getAnimatedValue();
                    searchWordPanelLayout.getLayoutParams().height = value.intValue();
                    searchWordPanelLayout.requestLayout();
                });

                AnimatorSet set = new AnimatorSet();
                set.playTogether(slideAnimator);
                set.setInterpolator(new AccelerateDecelerateInterpolator());
                set.start();

                if (!searchWordInputField.getText().toString().equals("")) {
                    translationWebView.clearMatches();
                    translationWebView.clearHistory();
                    translationWebView.findAllAsync(searchWordInputField.getText().toString());
                }

                if (themeId == R.style.CUSTOM_COLOR_THEME_LIGHT) {
                    searchWordButton.setImageResource(R.drawable.light_main_activity_search_with_down_arrow_button_image);
                } else if (themeId == R.style.CUSTOM_COLOR_THEME_DARK) {
                    searchWordButton.setImageResource(R.drawable.dark_main_activity_search_with_down_arrow_button_image);
                }

                inputFieldIsShown = false;
                translationWebView.clearMatches();
                searchWordInputField.setText("");

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchWordInputField.getWindowToken(), 0);

            }
        });

        scrollUpButton.setOnClickListener(v -> translationWebView.findNext(false));

        scrollDownButton.setOnClickListener(v -> {

            translationWebView.findNext(true);

        });

        scrollUpDefButton.setOnClickListener(v -> scrollUpDefault());
        scrollUpDefButton.setOnLongClickListener(v -> {

            ExercisesHelper exercisesHelper = new ExercisesHelper(ShowWordActivity.this);
            exercisesHelper.vibrate(ShowWordActivity.this, CommonConsts.SHORT_VIBRATION_TIME);

            translationWebView.scrollTo(0, 0);
            return true;
        });
        scrollDownDefButton.setOnClickListener(v -> scrollDownDefault());

        scrollDownDefButton.setOnLongClickListener(v -> {
            ExercisesHelper exercisesHelper = new ExercisesHelper(ShowWordActivity.this);
            exercisesHelper.vibrate(ShowWordActivity.this, CommonConsts.SHORT_VIBRATION_TIME);
            translationWebView.scrollTo(0, 100 * translationWebView.getContentHeight());
            return true;
        });
    }

    private void findStringInWebView() {
        translationWebView.clearMatches();
        translationWebView.clearHistory();

        translationWebView.findAllAsync(searchWordInputField.getText().toString());
    }

    TextView.OnEditorActionListener exampleListener = (exampleView, actionId, event) -> {
        if (actionId == EditorInfo.IME_NULL
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            findStringInWebView();
        } else if (actionId == EditorInfo.IME_ACTION_DONE) {
            findStringInWebView();
        } else if (actionId == EditorInfo.IME_ACTION_NEXT) {
            findStringInWebView();
        }
        return true;
    };

    private void goToManual() {

        Intent goToManual = new Intent(this, ManualActivity.class);

        goToManual.putExtra("calling_activity", "show_word_activity");

        this.startActivity(goToManual);

    }

    private void scrollUpDefault() {
        int filledTranslations = 0;
        for (StringBuilder stringBuilder : wordTranslationArray) {
            if (!stringBuilder.toString().equals(""))
                filledTranslations++;
        }

        if (filledTranslations == 1) {
            translationWebView.scrollTo(0, 0);
        } else {
            // Delay the scrollTo to make it work
            translationWebView.postDelayed(() -> {
                        currentDictPosition--;
                        if (currentDictPosition < 0) {
                            currentDictPosition = 0;
                        }

                        while (true) {
                            if (wordTranslationArray == null || wordTranslationArray.length == 0)
                                break;
                            if (!wordTranslationArray[currentDictPosition].toString().equals(""))
                                break;
                            else
                                currentDictPosition--;

                            if (currentDictPosition < 0) {
                                currentDictPosition = 0;
                                break;
                            }
                        }

                        String id = "dictPosition_" + currentDictPosition;
                        translationWebView.loadUrl("javascript:scrollAnchor(\"" + id + "\");");


                    }
                    , 200);
        }
    }

    private void scrollDownDefault() {
        int filledTranslations = 0;
        for (StringBuilder stringBuilder : wordTranslationArray) {
            if (!stringBuilder.toString().equals(""))
                filledTranslations++;
        }

        if (filledTranslations == 1) {
            translationWebView.scrollTo(0, 100 * translationWebView.getContentHeight());
        } else {
            // Delay the scrollTo to make it work
            translationWebView.postDelayed(() -> {
                        currentDictPosition++;
                        if (currentDictPosition >= wordTranslationArray.length) {
                            translationWebView.scrollTo(0, 100 * translationWebView.getContentHeight());
                            currentDictPosition = wordTranslationArray.length;
                        } else {
                            while (true) {
                                if (!wordTranslationArray[currentDictPosition].toString().equals(""))
                                    break;
                                else
                                    currentDictPosition++;

                                if (currentDictPosition >= wordTranslationArray.length) {
                                    translationWebView.scrollTo(0, 10 * translationWebView.getContentHeight());
                                    currentDictPosition = wordTranslationArray.length;
                                    break;
                                }
                            }
                            String id = "dictPosition_" + currentDictPosition;
                            translationWebView.loadUrl("javascript:scrollAnchor(\"" + id + "\");");
                        }

                    }
                    , 200);
        }

    }

    @SuppressLint("RestrictedApi")
    private void showWordActionsPanel(View v) {
        int[] ar = new int[2];
        v.getLocationOnScreen(ar);
        int onTouchY = ar[1];

        PopupMenu menu = new PopupMenu(ShowWordActivity.this, translationWebView);
        menu.inflate(R.menu.learner_user_dict_words_word_actions_menu);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.user_dict_words_word_actions_menu_search:

                    break;

            }
            return false;
        });

        @SuppressLint("RestrictedApi") MenuPopupHelper menuHelper = new MenuPopupHelper(ShowWordActivity.this, (MenuBuilder) menu.getMenu(), v);
        menuHelper.setForceShowIcon(true);
        menuHelper.show();
    }

    private final class SpeechEndReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            HideProgressBar();
            try {
                LocalBroadcastManager.getInstance(ShowWordActivity.this).unregisterReceiver(mSpeechEndReceiver);
            } catch (Exception e) {
                LogWriter.WriteToLog("EXC " + TAG + "SpeechEndReceiver ", e);
            }
        }
    }

    @Override
    public void onPause() {

        super.onPause();

        StringBuilder stringBuilder = new StringBuilder();
        if (wordTranslationArray != null) {
            for (StringBuilder tr : wordTranslationArray) {
                stringBuilder.append(tr);
            }

            Word wordToPut = new Word(
                    wordName,
                    transDirection,
                    newTranscription,
                    stringBuilder.toString(),
                    shortTranslation,
                    wordExample);

            if (!getIntent().getBooleanExtra("isFromHistory", false)) {

                CommonResult resOfPuttingToHistory =
                        CommonMethods.PutWordToHistoryTable(
                                wordToPut,
                                this);

                if (!resOfPuttingToHistory.isSuccess()) {
                    CommonMethods.ShowToastOnActivity(
                            resOfPuttingToHistory.getMessage(),
                            this);
                }
            }
        }
        if (dictsToSearch != null && dictsToSearch.size() > 0) {
            for (ISourceDict dict : dictsToSearch) {
                dict.CancelGettingWordArticle();
            }
        }

        if (delayedStopSpeechHandler != null)
            delayedStopSpeechHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onResume() {

        LogWriter.WriteToLog(TAG + " onResume()");

        super.onResume();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        CommonMethods.UnbindDrawables(findViewById(R.id.mainView));

    }

    @Override
    public void onBackPressed() {

        if (transcriptionField != null) {
            translationWebView.setVisibility(View.INVISIBLE);
        }
        if (transcriptionField != null) {
            transcriptionField.setVisibility(View.GONE);
        }

        super.onBackPressed();

        if (isFromWidget) {

            Intent intent = new Intent("kill");
            intent.setType("text/plain");
            LocalBroadcastManager.getInstance(MyApplication.getInstance()).sendBroadcast(intent);

            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
        }

        finishAfterTransition();
    }

}
