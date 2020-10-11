package com.enlearner.learner.popup_notifications_activities;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.enlearner.MyApplication;
import com.enlearner.R;
import com.enlearner.activities.ManualActivity;
import com.enlearner.common.CommonConsts;
import com.enlearner.common.CommonMethods;
import com.enlearner.common.CommonVariables;
import com.enlearner.common.LogWriter;
import com.enlearner.common.ThemesAndColorsHelper;
import com.enlearner.common.WordImageHelper;
import com.enlearner.common.backup_manager.GetDataFromGoogleDriveService;
import com.enlearner.common.backup_manager.GetDictsFromDriveTaskNew;
import com.enlearner.common.backup_manager.GoogleDriveConsumer;
import com.enlearner.common.backup_manager.GoogleDriveHelper;
import com.enlearner.common.backup_manager.WriteDictsToDriveTaskNew;
import com.enlearner.learner.ExercisesHelper;
import com.enlearner.online_services.FirebaseCrashlyticsHelper;
import com.enlearner.online_services.ForvoHelper;
import com.enlearner.preferences.DefSharedPrefsManager;
import com.enlearner.scheduler.PrepareNextRepeatSessionAT;
import com.enlearner.sql_helper.main_db.UserDictHelper;
import com.enlearner.sql_helper.main_db.UserDictSQLAdapter;
import com.enlearner.sql_helper.main_db.UserDictsListSQLAdapter;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;

import java.io.File;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import objects.CommonResult;
import objects.PopupExerciseCardItemLayout;
import objects.UserDictionary;
import objects.Word;
import services.BaseActivitySpeechServiceClient;
import services.MySpeechService;

public class PopUpActivityWithCard extends BaseActivitySpeechServiceClient
        implements GoogleDriveConsumer {

    //region VARIABLES

    public enum CallSources {
        FROM_NOTIFICATION
    }

    enum ExerciseTypes {
        WORD_TRANSLATION_TYPE(0),
        TRANSLATION_WORD_TYPE(1),
        SHOW_ALL_TYPE(2);

        public final int id;

        ExerciseTypes(int id) {
            this.id = id;
        }
    }

    public static final String CALL_SOURCE_INTENT_EXTRA = "click_source_intent_extra";

    public static boolean isOnRightAnswer = false;
    public static boolean isOnWrongAnswer = false;

    static final String SESSION_FINISHED_STATE = "isSessionFinished";
    static final String WORD_TO_DEAL_WITH = "wordToDealWith";
    static final String PREV_WORD_TO_DEAL_WITH = "prevWord";
    static final String PREV_NEXT_WORD_TO_DEAL_WITH = "prevNextWord";

    static final String TAG = PopUpActivityWithCard.class.getName();

    static final int REQUEST_CODE_DISCLAIMER = 6585;

    protected ExercisesHelper helper = new ExercisesHelper(this);

    protected Button rightGuessButton;
    protected Button wrongGuessButton;

    protected Button goToManualButton;

    int CARD_MARGIN_TOP = 60;
    int CARD_MARGIN_BOTTOM = 30;
    int CARD_MARGIN_HORIZONTAL = 40;

    View blockingView;
    ImageView expandedImageView;
    DraggableCoordinatorLayout cardParentContainer;

    ImageButton settingsButton;

    PopupMenu popup;

    Word prevWord;

    UserDictSQLAdapter userDictAdapter;

    Animation appearAnimation;
    Animation appearAnimation2;

    Animation scaleBitUpAnimation;
    Animation scaleBitDownAnimation;

    Animation translateBitUpAnimation;
    Animation translateBitDownAnimation;

    SharedPreferences sharedPreferences;

    boolean isSessionFinished;

    String toNativeTransDirection;

    SpeechEndReceiverGoNext mSpeechEndReceiver;
    SpeechEndReceiver mSpeechEndReceiverNoNext;

    boolean isUserAnswered = false;
    int blockViewCallsCounter = 0;
    int panelsColor2;

    //endregion

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        LogWriter.WriteToLog(TAG + " onCreate()");

        setTheme(MyApplication.getThemeId());
        super.onCreate(savedInstanceState);

        setContentView(R.layout.popup_exercises_activity_with_cards);

        ThemesAndColorsHelper.SetActivityStyleAttributes(this, false);

        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(PopUpActivityWithCard.this);

        CARD_MARGIN_TOP = (int) getResources().getDimension(R.dimen.popup_exercise_card_top_margin);
        CARD_MARGIN_BOTTOM = (int) getResources().getDimension(R.dimen.popup_exercise_card_bottom_margin);
        CARD_MARGIN_HORIZONTAL = (int) getResources().getDimension(R.dimen.popup_exercise_card_horizontal_margin);

        if (savedInstanceState != null) {
            isSessionFinished = savedInstanceState.getBoolean(SESSION_FINISHED_STATE);
            getCardView(2).SetWord((Word) savedInstanceState.getSerializable(WORD_TO_DEAL_WITH));
            prevWord = (Word) savedInstanceState.getSerializable(PREV_WORD_TO_DEAL_WITH);
            getCardView(1).SetWord((Word) savedInstanceState.getSerializable(PREV_NEXT_WORD_TO_DEAL_WITH));
        } else {
            isSessionFinished = false;
        }

        cardParentContainer = findViewById(R.id.parentContainer);

        LayoutInflater inflater;
        inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        PopupExerciseCardItemLayout layout1 = (PopupExerciseCardItemLayout) inflater.inflate(R.layout.popup_exercise_card_layout, null);
        PopupExerciseCardItemLayout layout2 = (PopupExerciseCardItemLayout) inflater.inflate(R.layout.popup_exercise_card_layout, null);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        layoutParams.topMargin = CARD_MARGIN_TOP;
        layoutParams.bottomMargin = CARD_MARGIN_BOTTOM;
        layoutParams.leftMargin = CARD_MARGIN_HORIZONTAL;
        layoutParams.rightMargin = CARD_MARGIN_HORIZONTAL;

        layout1.setLayoutParams(layoutParams);
        layout2.setLayoutParams(layoutParams);

        int elevation1 = CommonMethods.GetPixels(PopUpActivityWithCard.this, 5);
        int elevation2 = CommonMethods.GetPixels(PopUpActivityWithCard.this, 6);

        int buttonsColor = ThemesAndColorsHelper.getButtonsBackgroundColor();
        layout2.findViewById(R.id.popup_exercises_activity_answer_field_show_button).setBackgroundColor(buttonsColor);
        layout1.findViewById(R.id.popup_exercises_activity_answer_field_show_button).setBackgroundColor(buttonsColor);

        ViewCompat.setElevation(layout2, elevation2);
        ViewCompat.setElevation(layout1, elevation1);

        cardParentContainer.addView(layout1, 0);

        if (isStopAfterAnswer()) {
            ViewGroup allCardsParent = ((ViewGroup) cardParentContainer.getParent());
            allCardsParent.removeViewAt(1);
            allCardsParent.removeViewAt(1);
        } else {
            cardParentContainer.addView(layout2, 1);
        }

        setUpView();

        toNativeTransDirection = CommonMethods.GetCurFullTransDirection(CommonConsts.TO_NATIVE_TRANS_DIRECTION_NAME_SHORT);

        setCardBaseParameters(getCardView(2), true);
        setCardBaseParameters(getCardView(1), false);

        if (!isStopAfterAnswer()) {
            showFirstExercise(getCardView(1));
        }
        showFirstExercise(getCardView(2));

    }

    public PopupExerciseCardItemLayout getCardView(int cardNumber) {

        int childrenCount = cardParentContainer.getChildCount();
        if (childrenCount == 1) {
            cardNumber = 1;
        }

        return (PopupExerciseCardItemLayout) cardParentContainer.getChildAt(cardNumber - 1);
    }

    private void setUpView() {

        LogWriter.WriteToLog(TAG + " setUpView()");

        goToManualButton =
                ThemesAndColorsHelper.StyleAndGetGoToManualButtonAndSetHeader(
                        this,
                        getString(R.string.app_name),
                        true);

        ThemesAndColorsHelper.GetEnableSoundButton(this);

        rightGuessButton = findViewById(R.id.popup_exercises_activity_rightGuessButton);
        wrongGuessButton = findViewById(R.id.popup_exercises_activity_wrongGuessButton);
        settingsButton = findViewById(R.id.popup_exerc_settings_btn);

        blockingView = findViewById(R.id.popup_exercise_blocking_view);
        expandedImageView = findViewById(R.id.expanded_image);

        panelsColor2 = ThemesAndColorsHelper.getPanelsBackgroundColor2();

        View card3 = findViewById(R.id.popup_exercise_card_3);
        if (card3 != null) {
            Drawable bgCard3 = card3.getBackground();
            GradientDrawable shapeDrawableBackCard3 = (GradientDrawable) bgCard3;
            shapeDrawableBackCard3.setColor(panelsColor2);
        }

        View card4 = findViewById(R.id.popup_exercise_card_4);
        if (card4 != null) {
            Drawable bgCard4 = card4.getBackground();
            GradientDrawable shapeDrawableBackCard4 = (GradientDrawable) bgCard4;
            shapeDrawableBackCard4.setColor(panelsColor2);
        }

        cardParentContainer.addDraggableChild(getCardView(2));

        UserDictsListSQLAdapter udla = new UserDictsListSQLAdapter(this);
        udla.open();
        UserDictionary curUserDict = udla.getCurrentUserDict();
        if (curUserDict == null) {
            LogWriter.WriteToLog("ERR " + TAG + "setUpView()" + " can't find current user dict");
            return;
        }
        final String curDictName = curUserDict.getDictName();
        udla.close();
        userDictAdapter = new UserDictSQLAdapter(PopUpActivityWithCard.this, curDictName);

        appearAnimation = AnimationUtils
                .loadAnimation(PopUpActivityWithCard.this, R.anim.appear_animation);
        appearAnimation2 = AnimationUtils
                .loadAnimation(PopUpActivityWithCard.this, R.anim.appear_animation);

        scaleBitUpAnimation = AnimationUtils.loadAnimation(PopUpActivityWithCard.this, R.anim.scale_bit_up_animation);
        scaleBitUpAnimation.setFillAfter(true);
        scaleBitDownAnimation = AnimationUtils.loadAnimation(PopUpActivityWithCard.this, R.anim.scale_bit_down_animation);

        translateBitUpAnimation = new TranslateAnimation(0, 0, 0, -20);
        translateBitUpAnimation.setDuration(100);
        translateBitUpAnimation.setFillAfter(true);

        translateBitDownAnimation = new TranslateAnimation(0, 0, -20, 0);
        translateBitDownAnimation.setDuration(200);
        translateBitDownAnimation.setFillAfter(false);

        popup = new PopupMenu(PopUpActivityWithCard.this, settingsButton);
        popup.inflate(R.menu.popup_exerc_types);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);
        int defItemId = sp.getInt(DefSharedPrefsManager.popupExerciseType, ExerciseTypes.WORD_TRANSLATION_TYPE.id);
        if (defItemId == ExerciseTypes.WORD_TRANSLATION_TYPE.id) {
            popup.getMenu().findItem(R.id.word_translation_type).setChecked(true);
        } else if (defItemId == ExerciseTypes.TRANSLATION_WORD_TYPE.id) {
            popup.getMenu().findItem(R.id.translation_word_type).setChecked(true);
        } else if (defItemId == ExerciseTypes.SHOW_ALL_TYPE.id) {
            popup.getMenu().findItem(R.id.show_all_type).setChecked(true);
        } else {
            popup.getMenu().findItem(R.id.word_translation_type).setChecked(true);
        }

        cardParentContainer.setViewDragListener(new DraggableCoordinatorLayout.ViewDragListener() {

            int initialTop;
            int initialLeft;

            int currentTop;
            int currentLeft;

            @Override
            public void onViewPositionChanged(@NonNull View changedView, int left, int top, @Px int dx, @Px int dy, ViewDragHelper viewDragHelper) {

                currentTop = top;
                currentLeft = left;
                int threshold = 200;
                if (initialLeft - currentLeft > threshold && !isOnWrongAnswer) {
                    Log.v(TAG, "onWrongAnswer");
                    isOnWrongAnswer = true;
                    wrongGuessButton.startAnimation(translateBitUpAnimation);
                } else if (initialLeft - currentLeft < -threshold && !isOnRightAnswer) {
                    Log.v(TAG, "onRightAnswer");
                    isOnRightAnswer = true;
                    rightGuessButton.startAnimation(translateBitUpAnimation);
                } else if (-threshold < initialLeft - currentLeft && initialLeft - currentLeft < threshold
                ) {
                    Log.v(TAG, "not right or wrong");
                    if (isOnWrongAnswer) {
                        wrongGuessButton.startAnimation(translateBitDownAnimation);
                    }
                    if (isOnRightAnswer) {
                        rightGuessButton.startAnimation(translateBitDownAnimation);
                    }
                    isOnRightAnswer = false;
                    isOnWrongAnswer = false;
                }
            }

            @Override
            public void onViewCaptured(@NonNull View view, int pointerId) {

                Log.v(TAG, "onViewCaptured");
                initialTop = view.getTop();
                initialLeft = view.getLeft();

            }

            @Override
            public void onViewReleased(@NonNull View view, float vX, float vY, ViewDragHelper viewDragHelper) {

                Log.v(TAG, "onViewReleased vX=" + vX + "  vY=" + vY);

                if (!isOnWrongAnswer && !isOnRightAnswer) {

                    TranslateAnimation animation = new TranslateAnimation(0, initialLeft - currentLeft, 0, initialTop - currentTop);
                    animation.setDuration(150);
                    animation.setFillAfter(false);
                    animation.setInterpolator(new DecelerateInterpolator());
                    animation.setAnimationListener(new CardAnimationListener(view));
                    view.startAnimation(animation);

                } else {

                    if (isOnRightAnswer) {

                        if (vX < 0) {
                            vX = -vX;
                        }

                        if (vX < view.getWidth()) {
                            vX = view.getWidth();
                        }

                    } else {

                        if (vX > 0) {
                            vX = -vX;
                        }

                        if (Math.abs(vX) < view.getWidth()) {
                            vX = -view.getWidth();
                        }

                    }

                    AnimatorSet animSetXY = new AnimatorSet();

                    ObjectAnimator y = ObjectAnimator.ofFloat(view,
                            "translationY", 0, vY);

                    ObjectAnimator x = ObjectAnimator.ofFloat(view,
                            "translationX", 0, vX);

                    animSetXY.playTogether(x, y);
                    animSetXY.setInterpolator(new AccelerateInterpolator(1f));
                    animSetXY.setDuration(200);

                    onAnswer((PopupExerciseCardItemLayout) view, isOnRightAnswer);

                    animSetXY.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            blockingView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            blockingView.setVisibility(View.GONE);

                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });
                    animSetXY.start();
                }
            }
        });
    }

    private void setCardBaseParameters(PopupExerciseCardItemLayout cardView, boolean isFrontCard) {

        Drawable background = cardView.getBackground();
        GradientDrawable shapeDrawableBack = (GradientDrawable) background;
        shapeDrawableBack.setColor(panelsColor2);

        if (isFrontCard) {
            setButtonsListeners(cardView);
        }

        RelativeLayout wordLayout = cardView.findViewById(R.id.word_layout);
        CardView image1Card = cardView.findViewById(R.id.images_card_view_1);
        CardView image2Card = cardView.findViewById(R.id.images_card_view_2);

        Typeface typeFace = Typeface.createFromAsset(getAssets(), "fonts/L_10646.ttf");
        ((TextView) cardView.findViewById(R.id.popup_exercises_activity_transcriptionfield_upper)).setTypeface(typeFace);
        ((TextView) cardView.findViewById(R.id.popup_exercises_activity_transcriptionfield_lower)).setTypeface(typeFace);

        image1Card.setCardBackgroundColor(panelsColor2);
        image2Card.setCardBackgroundColor(panelsColor2);

        wordLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                wordLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                int width = wordLayout.getWidth();
                image1Card.getLayoutParams().width = width / 2;
                image2Card.getLayoutParams().width = width / 2;
                image1Card.requestLayout();
            }
        });
    }

    private void onAnswer(PopupExerciseCardItemLayout cardView, boolean isRightAnswer) {

        Log.v(TAG, "onAnswer() result = " + isRightAnswer);

        if (isStopAfterAnswer()) {
            Log.v(TAG, "onAnswer() BLOCK VIEW");
            localShowProgressBar(true);
        }

        new Handler().postDelayed(() -> {

            if (isRightAnswer) {
                rightGuessButton.startAnimation(translateBitDownAnimation);
            } else {
                wrongGuessButton.startAnimation(translateBitDownAnimation);
            }

            resetCard(cardView, isRightAnswer);

            Log.v(TAG, "onAnswer() UN BLOCK VIEW");

            localHideProgressBar();

        }, 180);

    }

    private void resetCard(PopupExerciseCardItemLayout cardView, boolean isRightAnswer) {

        isOnRightAnswer = false;
        isOnWrongAnswer = false;

        if (isStopAfterAnswer()) {
            if (isRightAnswer) {
                onRightAnswer(cardView, null);
            } else {
                onWrongAnswer(cardView, null);
            }
            return;
        }

        PopupExerciseCardItemLayout newFrontCard = (PopupExerciseCardItemLayout) cardParentContainer.getChildAt(0);

        cardParentContainer.removeAllDraggableChildren();
        cardParentContainer.removeAllViews();

        LayoutInflater inflater;
        inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        PopupExerciseCardItemLayout newBackCard = (PopupExerciseCardItemLayout) inflater.inflate(R.layout.popup_exercise_card_layout, null);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        layoutParams.topMargin = CARD_MARGIN_TOP;
        layoutParams.bottomMargin = CARD_MARGIN_BOTTOM;
        layoutParams.leftMargin = CARD_MARGIN_HORIZONTAL;
        layoutParams.rightMargin = CARD_MARGIN_HORIZONTAL;

        newBackCard.setLayoutParams(layoutParams);

        int buttonsColor = ThemesAndColorsHelper.getButtonsBackgroundColor();
        newBackCard.findViewById(R.id.popup_exercises_activity_answer_field_show_button).setBackgroundColor(buttonsColor);

        int elevation1 = CommonMethods.GetPixels(PopUpActivityWithCard.this, 2);

        ViewCompat.setElevation(newBackCard, elevation1);

        cardParentContainer.addView(newBackCard, 0);
        cardParentContainer.addView(newFrontCard, 1);

        cardParentContainer.addDraggableChild(newFrontCard);

        if (isRightAnswer) {
            onRightAnswer(cardView, newBackCard);
        } else {
            onWrongAnswer(cardView, newBackCard);
        }

        if (!isStopAfterAnswer()) {
            setCardBaseParameters(newFrontCard, true);
            setCardBaseParameters(newBackCard, false);
        }

        final int[] counter = {1};
        Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                counter[0]++;
                if (counter[0] < 7) {
                    int elev = CommonMethods.GetPixels(PopUpActivityWithCard.this, counter[0]);
                    newFrontCard.setElevation(elev);
                    handler.postDelayed(this, 30);
                } else {
                    counter[0] = 1;
                }
            }
        };
        handler.postDelayed(runnable, 30);

        newFrontCard.startAnimation(scaleBitUpAnimation);

    }

    public void showFirstExercise(PopupExerciseCardItemLayout cardView) {
        showNextSameExercise(cardView, true, true);
    }

    public void showNextExercise(PopupExerciseCardItemLayout cardView) {
        Log.v(TAG, "showNextExercise()");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);
        boolean enableSayOnAnswer = sp.getBoolean(DefSharedPrefsManager.enableSayOnAnswerKey, true);
        if (enableSayOnAnswer) {

            boolean isForvoEnabled = sp.getBoolean(ForvoHelper.isEnableForvoKey, false);
            if (isForvoEnabled) {
                Handler handler = new Handler();
                handler.postDelayed(() -> showNextSameExercise(cardView, true, true), ExercisesHelper.delayBeforeNextExerciseLong);
            } else {
                Log.v(TAG, "showNextExercise() BLOCK VIEW");
                localShowProgressBar(true);

                mSpeechEndReceiver = new SpeechEndReceiverGoNext();
                IntentFilter filter = IntentFilter.create(MySpeechService.SPEECH_END_INTENT_ACTION, "text/plain");

                LocalBroadcastManager.getInstance(this).registerReceiver(mSpeechEndReceiver, filter);
            }
        }
        showNextSameExercise(cardView, true, true);
    }

    protected boolean isStopAfterAnswer() {
        return false;
    }

    protected void localShowProgressBar(boolean isBlockView) {
        ShowProgressBar();
        if (isBlockView) {
            blockingView.setVisibility(View.VISIBLE);
        }
        blockViewCallsCounter++;
    }

    protected void localHideProgressBar() {
        blockViewCallsCounter--;
        if (blockViewCallsCounter <= 0) {
            HideProgressBar();
            blockingView.setVisibility(View.GONE);
            blockViewCallsCounter = 0;
        }
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {

        savedInstanceState.putBoolean(SESSION_FINISHED_STATE, isSessionFinished);
        savedInstanceState.putSerializable(WORD_TO_DEAL_WITH, getCardView(2).GetWord());
        savedInstanceState.putSerializable(PREV_WORD_TO_DEAL_WITH, prevWord);
        savedInstanceState.putSerializable(PREV_NEXT_WORD_TO_DEAL_WITH, getCardView(1).GetWord());

        super.onSaveInstanceState(savedInstanceState);
    }

    private void setButtonsListeners(PopupExerciseCardItemLayout cardItem) {

        rightGuessButton.setOnClickListener(v -> {

            TranslateAnimation animation = new TranslateAnimation(0, 2 * cardItem.getWidth(), 0, 0);
            animation.setDuration(250);
            animation.setFillAfter(true);
            animation.setInterpolator(new AccelerateInterpolator());
            animation.setAnimationListener(new Animation.AnimationListener() {

                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    resetCard(cardItem, true);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            cardItem.startAnimation(animation);

        });

        wrongGuessButton.setOnClickListener(v -> {

            TranslateAnimation animation = new TranslateAnimation(0, -2 * cardItem.getWidth(), 0, 0);
            animation.setDuration(250);
            animation.setFillAfter(true);
            animation.setInterpolator(new AccelerateInterpolator());

            animation.setAnimationListener(new Animation.AnimationListener() {

                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    resetCard(cardItem, false);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            cardItem.startAnimation(animation);

        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                popup.setOnMenuItemClickListener(item -> {

                    switch (item.getItemId()) {
                        case R.id.word_translation_type:
                            savePref(R.id.word_translation_type);
                            item.setChecked(!item.isChecked());

                            break;
                        case R.id.translation_word_type:
                            savePref(R.id.translation_word_type);
                            item.setChecked(!item.isChecked());
                            break;
                        case R.id.show_all_type:
                            savePref(R.id.show_all_type);
                            item.setChecked(!item.isChecked());
                            break;
                        default:
                            break;
                    }
                    showFirstExercise(getCardView(1));
                    return false;
                });
                popup.show();
            }

            private void savePref(int typeId) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);
                SharedPreferences.Editor editor = sp.edit();
                if (typeId == R.id.word_translation_type) {
                    editor.putInt(DefSharedPrefsManager.popupExerciseType, 0);
                } else if (typeId == R.id.translation_word_type) {
                    editor.putInt(DefSharedPrefsManager.popupExerciseType, 1);
                } else if (typeId == R.id.show_all_type) {
                    editor.putInt(DefSharedPrefsManager.popupExerciseType, 2);
                }
                editor.apply();
            }
        });

        cardItem.findViewById(R.id.popup_exercises_activity_answer_field_show_button).setOnClickListener(v -> showAnswer(cardItem));

        goToManualButton.setOnClickListener(v -> goToManual());

        cardItem.findViewById(R.id.popup_exerc_activity_say_btn_lower).setOnClickListener(view -> sayWord(cardItem));
        cardItem.findViewById(R.id.popup_exerc_activity_say_btn).setOnClickListener(view -> sayWord(cardItem));

        cardItem.findViewById(R.id.popup_exerc_activity_say_btn_lower_center).setOnClickListener(view -> sayWord(cardItem));
        cardItem.findViewById(R.id.popup_exerc_activity_say_btn_center).setOnClickListener(view -> sayWord(cardItem));
    }

    private void sayWord(PopupExerciseCardItemLayout cardLayout) {
        if (cardLayout.GetWord() == null) {
            return;
        }

        String wordTransDir = cardLayout.GetWord().getTransDirection();
        String wordName = cardLayout.GetWord().getName();
        if (wordTransDir != null
                && !wordTransDir.equals("")) {

            String wordLanguage = wordTransDir.substring(0, 2);
            MyApplication.getInstance().Speak(wordName, wordLanguage);

            Log.v(TAG, "say button clicked BLOCK VIEW");
            localShowProgressBar(false);

            mSpeechEndReceiverNoNext = new SpeechEndReceiver();
            IntentFilter filter = IntentFilter.create(MySpeechService.SPEECH_END_INTENT_ACTION, "text/plain");
            LocalBroadcastManager.getInstance(PopUpActivityWithCard.this).registerReceiver(mSpeechEndReceiverNoNext, filter);
        }
    }

    private void showAnswer(PopupExerciseCardItemLayout cardView) {

        LogWriter.WriteToLog(TAG + " showAnswer()");

        if (cardView.GetWord() == null) {
            String message = "ERROR " + TAG + " wordToDealWith == null";
            LogWriter.WriteToLog(message);
            Exception e = new Exception(message);
            new FirebaseCrashlyticsHelper().SendSilentError(e);
            finish();
            return;
        }

        cardView.findViewById(R.id.popup_exercises_activity_answer_field_show_button).setVisibility(View.GONE);
        cardView.findViewById(R.id.popup_exercises_activity_textfield_lower).setVisibility(View.VISIBLE);

        Animation scaleAnimation1 = AnimationUtils.loadAnimation(MyApplication.getInstance(), R.anim.scale_animation);
        cardView.findViewById(R.id.popup_exercises_activity_textfield_lower).startAnimation(scaleAnimation1);

        int exerciseType = getCurrentExerciseType();
        if (exerciseType == ExerciseTypes.TRANSLATION_WORD_TYPE.id) {
            cardView.findViewById(R.id.popup_exercises_activity_transcriptionfield_lower_layout).setVisibility(View.VISIBLE);
//            ImageButton sayButton = cardView.findViewById(R.id.popup_exerc_activity_say_btn_lower);

            if (cardView.GetWord().getWordTranscription() != null && !cardView.GetWord().getWordTranscription().equals("")) {
                cardView.findViewById(R.id.popup_exercises_activity_transcriptionfield_lower).setVisibility(View.VISIBLE);
                cardView.findViewById(R.id.popup_exercises_activity_transcriptionfield_lower).startAnimation(scaleAnimation1);
                cardView.findViewById(R.id.popup_exerc_activity_say_btn_lower).setVisibility(View.VISIBLE);
                cardView.findViewById(R.id.popup_exerc_activity_say_btn_lower_center).setVisibility(View.GONE);
            } else {
                cardView.findViewById(R.id.popup_exercises_activity_transcriptionfield_lower).setVisibility(View.GONE);
                cardView.findViewById(R.id.popup_exerc_activity_say_btn_lower).setVisibility(View.GONE);
                cardView.findViewById(R.id.popup_exerc_activity_say_btn_lower_center).setVisibility(View.VISIBLE);
            }

        }

    }

    private void onRightAnswer(PopupExerciseCardItemLayout cardView, PopupExerciseCardItemLayout nextCardView) {

        Word word = cardView.GetWord();

        LogWriter.WriteToLog(TAG + " onRightAnswer(word = " + (word == null ? "null" : word.getName()) + ")");

        if (word == null) {
            LogWriter.WriteToLog("ERROR " + TAG + " onRightAnswer(word = NULL)");
            return;
        }

        isUserAnswered = true;

        final Word finalWord = cardView.GetWord();

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);
        boolean enableSayOnAnswer = sp.getBoolean(DefSharedPrefsManager.enableSayOnAnswerKey, true);
        if (enableSayOnAnswer) {

            String wordTransDir = finalWord.getTransDirection();
            if (wordTransDir != null && !wordTransDir.equals("")) {
                String wordLanguage = wordTransDir.substring(0, 2);
                MyApplication.getInstance().Speak(finalWord.getName(), wordLanguage);
            }
        }

        UserDictHelper userDictHelper = new UserDictHelper(PopUpActivityWithCard.this);
        if (userDictHelper.IsSessionFinished()) {
            LogWriter.WriteToLog(TAG + " onRightAnswer() userDictHelper.IsSessionFinished() == TRUE");

            ExercisesHelper.ShowSessionFinishedSign(this, true, true);

            showNextExercise(nextCardView);
        } else if (finalWord == null) {
            LogWriter.WriteToLog(TAG + " onRightAnswer() word == null");

            showNextExercise(nextCardView);
        } else if (finalWord.isWordLearned()) {
            LogWriter.WriteToLog(TAG + " onRightAnswer() word.isWordLearned() == true");

            AlertDialog.Builder ad;
            String title = finalWord.getName() + " - " + getString(R.string.word_memorized) + "?";
            String button1String = getString(R.string.yes);
            String button2String = getString(R.string.no);

            ad = new AlertDialog.Builder(PopUpActivityWithCard.this);
            ad.setMessage(title);

            ad.setPositiveButton(
                    button1String, (dialog, arg1) -> {
                        CommonResult resOfMakingWordLearned =
                                helper.WordIsLearnedAction(PopUpActivityWithCard.this, finalWord);

                        if (resOfMakingWordLearned.isSuccess()) {
                            Toast.makeText(
                                    PopUpActivityWithCard.this,
                                    PopUpActivityWithCard.this.getResources().getString(R.string.word_added_to_learned),
                                    Toast.LENGTH_SHORT).show();
                        }

                        showNextExercise(cardView);
                    });

            ad.setNegativeButton(
                    button2String, (dialog, arg1) -> {
                        helper.WordIsNotLearnedAction(PopUpActivityWithCard.this, finalWord);

                        showNextExercise(cardView);

                    });

            AlertDialog dialog1 = ad.show();
            dialog1.setCancelable(false);
            dialog1.setCanceledOnTouchOutside(false);
            TextView messageView1 = dialog1.findViewById(android.R.id.message);
            messageView1.setGravity(Gravity.CENTER);
            messageView1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);

        } else {

            LogWriter.WriteToLog(TAG + " onRightAnswer() update word's points");

            userDictHelper.UpdateItemsPointsAndSetRepeatTime(finalWord, CommonConsts.RIGHT_ANSWER_POINTS_SMALL, true, false);

            showNextExercise(nextCardView);
        }

    }

    private void onWrongAnswer(PopupExerciseCardItemLayout cardView, PopupExerciseCardItemLayout nextCardView) {

        Word word = cardView.GetWord();

        LogWriter.WriteToLog(TAG + " onWrongAnswer(_word = " + (word == null ? "null" : word.getName()) + ")");

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);

        UserDictHelper userDictHelper = new UserDictHelper(PopUpActivityWithCard.this);

        if (word == null) {
            LogWriter.WriteToLog("ERROR " + TAG + " onWrongAnswer(word = NULL)");

            showNextExercise(nextCardView);
            return;
        }

        isUserAnswered = true;

        boolean enableSayOnAnswer = sp.getBoolean(DefSharedPrefsManager.enableSayOnAnswerKey, true);
        if (enableSayOnAnswer) {
            String wordTransDir = word.getTransDirection();
            if (wordTransDir != null && !wordTransDir.equals("")) {
                String wordLanguage = wordTransDir.substring(0, 2);
                MyApplication.getInstance().Speak(word.getName(), wordLanguage);
            }
        }

        userDictHelper.UpdateItemsPointsAndSetRepeatTime(word, 0, true, false);

        helper.onWrongAnswer(PopUpActivityWithCard.this, word);

        showNextExercise(nextCardView);

    }

    public void ShowDisclaimer(boolean _isShowDisclaimer) {
        String callSource = getIntent().getStringExtra(CALL_SOURCE_INTENT_EXTRA);
        if (!_isShowDisclaimer || (callSource != null && callSource.equals(CallSources.FROM_NOTIFICATION.toString()))) {
            return;
        }
//        if (_isShowDisclaimer) {
        final String pupUpDiscKey = "popUpExerciseDisclaimerShown";
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);
        boolean hasDisclaimerBeenShown = prefs.getBoolean(pupUpDiscKey, false);
        if (!hasDisclaimerBeenShown) {
            boolean isEnablePopupExercise = prefs.getBoolean(DefSharedPrefsManager.enablePopUpNotificationsKey, false);
            if (isEnablePopupExercise) {
                Intent showDisclaimer = new Intent(
                        PopUpActivityWithCard.this,
                        PopUpExerciseDisclaimerShort.class);
                startActivityForResult(showDisclaimer, REQUEST_CODE_DISCLAIMER);
            }
        }
//        }
    }

    public void showNextSameExercise(PopupExerciseCardItemLayout cardView, final boolean _isShowDisclaimer, final boolean _isAnimateSessionFinishedSign) {

        LogWriter.WriteToLog(TAG + " showNextSameExercise()");

        ExercisesHelper.ShowSessionFinishedSignIfNeeded(PopUpActivityWithCard.this, _isAnimateSessionFinishedSign);

        final Handler handler = new Handler();
        Runnable runnable = () -> {

            Word word = getWordFromDb();

            LogWriter.WriteToLog(TAG + " showNextSameExercise() wordToDealWith = " + word.getName());

            cardView.SetWord(word);

            handler.post(() -> {

                setActivityToInitialState(cardView);

                fillActivityFields(cardView, getCurrentExerciseType());

                ShowDisclaimer(_isShowDisclaimer);
            });
        };

        Thread trd = new Thread(runnable);
        Thread.UncaughtExceptionHandler excHandler = (th, ex) -> {
            Toast.makeText(PopUpActivityWithCard.this, "EXC occured!", Toast.LENGTH_SHORT).show();
            LogWriter.WriteToLog(TAG + " showNextSameExercise()", ex);
        };

        trd.setUncaughtExceptionHandler(excHandler);
        trd.start();
    }

    private int getCurrentExerciseType() {
        SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);

        return sp.getInt(DefSharedPrefsManager.popupExerciseType, R.id.word_translation_type);
    }

    private Word getWordFromDb() {

        LogWriter.WriteToLog(TAG + " getWordFromDb()");

        Word wordFromDb;

        try {

            CommonResult resOfGettingNextWord =
                    helper.getNextWord(
                    );

            if (resOfGettingNextWord == null) {
                LogWriter.WriteToLog(TAG + " getWordFromDb() resOfGettingNextWord = NULL");
                return null;
            }

            Word wordName = (Word) resOfGettingNextWord.getReturnedObject();

            LogWriter.WriteToLog(TAG + " getWordFromDb() nextWord from ExHelper = " + wordName.getName());

            wordFromDb = wordName;

            prevWord = wordName;

        } catch (Exception e) {
            LogWriter.WriteToLog("EXC " + TAG + "getInfoFromDb() " + e.getMessage(), e);
            return null;
        }

        return wordFromDb;
    }

    private void goToManual() {

        Intent goToManual = new Intent(this, ManualActivity.class);
        goToManual.putExtra("calling_activity", "popup_exercise_activity");
        this.startActivity(goToManual);

    }

    private void setActivityToInitialState(View cardLayout) {

        LogWriter.WriteToLog(TAG + " setActivityToInitialState()");

        cardLayout.findViewById(R.id.popup_exercises_activity_answer_field_show_button).clearAnimation();

        cardLayout.findViewById(R.id.popup_exercises_activity_textfield_lower).clearAnimation();
        cardLayout.findViewById(R.id.popup_exercises_activity_textfield_lower).setVisibility(View.GONE);
        cardLayout.findViewById(R.id.popup_exercises_activity_textfield_upper).clearAnimation();
        cardLayout.findViewById(R.id.popup_exercises_activity_textfield_upper).setVisibility(View.GONE);
        cardLayout.findViewById(R.id.popup_exercises_activity_transcriptionfield_upper).clearAnimation();
        cardLayout.findViewById(R.id.popup_exercises_activity_transcriptionfield_upper).setVisibility(View.GONE);
        cardLayout.findViewById(R.id.popup_exercises_activity_transcriptionfield_lower).clearAnimation();

        cardLayout.findViewById(R.id.popup_exercises_activity_transcriptionfield_upper).setTranslationX(0);
        cardLayout.findViewById(R.id.popup_exerc_activity_say_btn).setVisibility(View.VISIBLE);
        cardLayout.findViewById(R.id.popup_exerc_activity_say_btn_center).setVisibility(View.GONE);
//        cardLayout.findViewById(R.id.popup_exercises_activity_transcriptionfield_lower).setVisibility(View.GONE);

    }

    private void fillActivityFields(PopupExerciseCardItemLayout cardItem, int exerciseType) {

        Word wordData = cardItem.GetWord();

        LogWriter.WriteToLog(TAG + " fillActivityFields( _data = " + (wordData == null ? "null" : "OK") + ")");

        if (wordData == null) {
            Log.v(TAG, "data = null");
            return;
        }

        ImageButton answerFieldShowButton = cardItem.findViewById(R.id.popup_exercises_activity_answer_field_show_button);
        ImageButton sayButton = cardItem.findViewById(R.id.popup_exerc_activity_say_btn);
        ImageButton sayButtonCenter = cardItem.findViewById(R.id.popup_exerc_activity_say_btn_center);
        TextView lowerTextField = cardItem.findViewById(R.id.popup_exercises_activity_textfield_lower);
        TextView upperTextField = cardItem.findViewById(R.id.popup_exercises_activity_textfield_upper);
        TextView transcriptionFieldUpper = cardItem.findViewById(R.id.popup_exercises_activity_transcriptionfield_upper);
        TextView transcriptionFieldLower = cardItem.findViewById(R.id.popup_exercises_activity_transcriptionfield_lower);
        View imagesLayout = cardItem.findViewById(R.id.images_layout);
        View fillerView = cardItem.findViewById(R.id.filler_view);
        CardView image1Card = cardItem.findViewById(R.id.images_card_view_1);
        CardView image2Card = cardItem.findViewById(R.id.images_card_view_2);
        ImageView image1 = cardItem.findViewById(R.id.activity_record_word_image_1);
        ImageView image2 = cardItem.findViewById(R.id.activity_record_word_image_2);

        imagesLayout.setVisibility(View.GONE);
        fillerView.setVisibility(View.VISIBLE);

        Bitmap image1Bitmap = null;
        if (wordData.getImage1() != null) {
            File imgFile = new File(wordData.getImage1());
            if (imgFile.exists()) {
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                image1Bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath(), bmOptions);
            }
        }

        Bitmap image2Bitmap = null;
        if (wordData.getImage2() != null) {
            File imgFile = new File(wordData.getImage2());
            if (imgFile.exists()) {
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                image2Bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath(), bmOptions);
            }
        }

        Bitmap finalImage1Bitmap = image1Bitmap;
        Bitmap finalImage2Bitmap = image2Bitmap;

        boolean isShowImages = false;

        if (finalImage1Bitmap != null) {
            isShowImages = true;

            image1Card.setVisibility(View.VISIBLE);
            image1.setImageBitmap(finalImage1Bitmap);

        } else {

            image1Card.setVisibility(View.GONE);

        }

        if (finalImage2Bitmap != null) {
            isShowImages = true;

            image2Card.setVisibility(View.VISIBLE);
            image2.setImageBitmap(finalImage2Bitmap);

        } else {

            image2Card.setVisibility(View.GONE);

        }

        if (isShowImages) {

            imagesLayout.setVisibility(View.VISIBLE);
            fillerView.setVisibility(View.GONE);

        }

        image1.setOnClickListener(null);
        image1.setOnClickListener(view -> WordImageHelper.zoomImageFromThumb(image1, expandedImageView, findViewById(R.id.mainView), WordImageHelper.WordImageTarget.FOR_WORD, wordData.getName(), null));
        image2.setOnClickListener(null);
        image2.setOnClickListener(view -> WordImageHelper.zoomImageFromThumb(image2, expandedImageView, findViewById(R.id.mainView), WordImageHelper.WordImageTarget.FOR_TRANSLATION, wordData.getName(), null));

//        SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);


        if (exerciseType == ExerciseTypes.WORD_TRANSLATION_TYPE.id) {

            upperTextField.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.popup_exercise_upper_field_font_size));
            lowerTextField.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.popup_exercise_lower_field_font_size));

            upperTextField.setText(Html.fromHtml(wordData.getName()));
            upperTextField.setVisibility(View.VISIBLE);
            lowerTextField.setText(Html.fromHtml(wordData.getWordShortTranslation()));
            answerFieldShowButton.setVisibility(View.VISIBLE);
            sayButton.setVisibility(View.VISIBLE);

            if (wordData.getWordTranscription() != null && !wordData.getWordTranscription().equals("")) {

                        transcriptionFieldUpper.setText(Html.fromHtml(wordData.getWordTranscription()));
                        transcriptionFieldUpper.setVisibility(View.VISIBLE);
                        sayButton.setVisibility(View.VISIBLE);
                        sayButtonCenter.setVisibility(View.GONE);

            } else {
                transcriptionFieldUpper.setVisibility(View.GONE);
                sayButton.setVisibility(View.GONE);
                sayButtonCenter.setVisibility(View.VISIBLE);
            }

        } else if (exerciseType == ExerciseTypes.TRANSLATION_WORD_TYPE.id) {

            upperTextField.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.popup_exercise_lower_field_font_size));
            lowerTextField.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.popup_exercise_upper_field_font_size));

            answerFieldShowButton.setVisibility(View.VISIBLE);

            upperTextField.setText(Html.fromHtml(wordData.getWordShortTranslation()));
            upperTextField.setMaxLines(3);
            upperTextField.setVisibility(View.VISIBLE);
            lowerTextField.setText(Html.fromHtml(wordData.getName()));

            transcriptionFieldUpper.setVisibility(View.GONE);
            sayButton.setVisibility(View.GONE);

            if (wordData.getWordTranscription() != null) {
                transcriptionFieldLower.setText(Html.fromHtml(wordData.getWordTranscription()));
            }


        } else if (exerciseType == ExerciseTypes.SHOW_ALL_TYPE.id) {

            upperTextField.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.popup_exercise_upper_field_font_size));
            lowerTextField.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.popup_exercise_lower_field_font_size));

            upperTextField.setText(Html.fromHtml(wordData.getName()));
            upperTextField.setVisibility(View.VISIBLE);
            lowerTextField.setText(Html.fromHtml(wordData.getWordShortTranslation()));

            if (wordData.getWordTranscription() != null && !wordData.getWordTranscription().equals("")) {

                transcriptionFieldUpper.setText(Html.fromHtml(wordData.getWordTranscription()));
                transcriptionFieldUpper.setVisibility(View.VISIBLE);
                sayButton.setVisibility(View.VISIBLE);
                sayButtonCenter.setVisibility(View.GONE);

            } else {
                transcriptionFieldUpper.setVisibility(View.GONE);
                sayButton.setVisibility(View.GONE);
                sayButtonCenter.setVisibility(View.VISIBLE);
            }

            answerFieldShowButton.setVisibility(View.GONE);
            lowerTextField.setVisibility(View.VISIBLE);

        }

    }

    private static class CardAnimationListener implements Animation.AnimationListener {

        private final View cardView;

        public CardAnimationListener(View _cardView) {
            cardView = _cardView;
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            cardView.clearAnimation();

            CoordinatorLayout.LayoutParams layoutParams =
                    (CoordinatorLayout.LayoutParams) cardView.getLayoutParams();
            layoutParams.gravity = Gravity.CENTER;
            cardView.requestLayout();

        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }

    }

    private void prepareNextRepeatSession() {
        PrepareNextRepeatSessionAT prepareNextRepeatSessionAT =
                new PrepareNextRepeatSessionAT(PopUpActivityWithCard.this);

        prepareNextRepeatSessionAT.execute();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_DISCLAIMER) {
            if (resultCode == RESULT_CANCELED) {
                MyOnBackPressed();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {

        LogWriter.WriteToLog(TAG + " onBackPressed()");

        super.onBackPressed();

        MyOnBackPressed();

        cardParentContainer.removeAllViews();
    }

    public void MyOnBackPressed() {

        if (isUserAnswered) {
            SyncWithGoogleDrive(false);
        }

        prepareNextRepeatSession();

        finish();

    }

    protected void MyOnPause() {
        //to implement in children
    }

    @Override
    protected void onPause() {
        LogWriter.WriteToLog(TAG + " onPause()");
        super.onPause();

        MyOnPause();
    }

    @Override
    protected void onResume() {
        LogWriter.WriteToLog(TAG + " onResume()");

        super.onResume();
        ExercisesHelper.ShowSessionFinishedSignIfNeeded(PopUpActivityWithCard.this, false);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (userDictAdapter != null) {
            userDictAdapter.close(TAG + " onDestroy()");
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        CommonVariables.EXERCISES_IN_PROGRESS = true;
        MyApplication.getInstance().setIsExerciseRunning(true);
    }

    @Override
    protected void onStop() {
        super.onStop();

        CommonVariables.EXERCISES_IN_PROGRESS = false;
        MyApplication.getInstance().setIsExerciseRunning(false);
    }

    private void SyncWithGoogleDrive(boolean isGetData) {
        Log.v(TAG, "SyncWithGoogleDrive isGetData = " + isGetData);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PopUpActivityWithCard.this);
        boolean isGoogleDriveAutoSyncEnabled = sp.getBoolean(GoogleDriveHelper.IS_AUTO_SYNC_ENABLED_KEY, false);
        if (isGoogleDriveAutoSyncEnabled
                && !GetDictsFromDriveTaskNew.IsTaskRunning
                && !WriteDictsToDriveTaskNew.IsTaskRunning) {
            GoogleDriveHelper.mCredential = GoogleAccountCredential.usingOAuth2(
                    getApplicationContext(), Arrays.asList(GoogleDriveHelper.SCOPES))
                    .setBackOff(new ExponentialBackOff());

            GetResultsFromApi(isGetData);
        }
    }

    @Override
    public void GetResultsFromApi(boolean isGetData) {
        if (!GoogleDriveHelper.isGooglePlayServicesAvailable(PopUpActivityWithCard.this)) {
            GoogleDriveHelper.acquireGooglePlayServices(PopUpActivityWithCard.this);
        } else if (GoogleDriveHelper.mCredential == null || GoogleDriveHelper.mCredential.getSelectedAccountName() == null) {
            GoogleDriveHelper.chooseAccount(this, GoogleDriveHelper.mCredential, isGetData);
        } else if (!GoogleDriveHelper.isDeviceOnline(PopUpActivityWithCard.this)) {
            Toast.makeText(this, getString(R.string.check_internet_connection), Toast.LENGTH_SHORT).show();
        } else {

            Intent getData = new Intent(PopUpActivityWithCard.this, GetDataFromGoogleDriveService.class);
            getData.putExtra("isGetData", isGetData);
            startService(getData);

        }
    }

    private final class SpeechEndReceiverGoNext extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "SpeechEndReceiverGoNext() UN BLOCK VIEW");
            localHideProgressBar();
            LocalBroadcastManager.getInstance(PopUpActivityWithCard.this).unregisterReceiver(mSpeechEndReceiver);
        }
    }

    private final class SpeechEndReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            localHideProgressBar();
            LocalBroadcastManager.getInstance(PopUpActivityWithCard.this).unregisterReceiver(mSpeechEndReceiverNoNext);
        }
    }
}
