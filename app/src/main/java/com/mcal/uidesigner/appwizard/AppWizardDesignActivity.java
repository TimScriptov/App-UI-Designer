package com.mcal.uidesigner.appwizard;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import com.mcal.uidesigner.R;
import com.mcal.uidesigner.appwizard.runtime.AppWizardActivity;
import com.mcal.uidesigner.appwizard.runtime.AppWizardProject;
import com.mcal.uidesigner.common.MessageBox;
import com.mcal.uidesigner.common.PositionalXMLReader;
import com.mcal.uidesigner.common.UndoManager;
import com.mcal.uidesigner.utils.Utils;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

public class AppWizardDesignActivity extends AppWizardActivity implements UndoManager.UndoRedoListener {
    private static final String APP_WIZARD_SETTINGS = "AppWizard";
    private static final String PREF_APPWIZARD_EDITMODE = "PREF_APPWIZARD_EDITMODE";
    private final AppWizardPropertiesEditor editor = new AppWizardPropertiesEditor(this);
    private boolean isInitialized;
    private UndoManager undoManager;

    public String getResDirPath() {
        return getProjectPath() + "/res";
    }

    public String getLayoutFilePath(String layoutName) {
        if (layoutName != null) {
            return getResDirPath() + "/layout/" + layoutName + ".xml";
        }
        return null;
    }

    public String createFragmentLayout(int id) {
        String layoutName = "fragment" + (id + 1);
        getAppActivity().getFragment(id).setLayoutNoRefresh(layoutName);
        return getLayoutFilePath(layoutName);
    }

    @NonNull
    private String getProjectFilepath() {
        return getProjectPath() + "/assets/app.xml";
    }

    public String getProjectPath() {
        return Utils.getSDCardPath() + "/AppProjects/AppWizard";
    }

    @Override
    public void revertToVersion(@NonNull String filepath, String content, int change) {
        if (filepath.equals(getProjectFilepath())) {
            try {
                saveXml(content);
                getProject().revertToVersion(parseXml(content), change);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void undoRedoStateChanged() {
        if (this.isInitialized) {
            refreshButtons();
        }
    }

    @Override
    public Document loadXml() {
        try {
            String xml = Utils.readFileAsString(getProjectFilepath());
            this.undoManager.addBaseVersion(getProjectFilepath(), xml, 0);
            return parseXml(xml);
        } catch (Exception e) {
            return null;
        }
    }

    private Document parseXml(@NonNull String xml) throws IOException, SAXException {
        InputStream in = new ByteArrayInputStream(xml.getBytes());
        Document document = PositionalXMLReader.readXML(in);
        in.close();
        return document;
    }

    @Override
    public void saveXml(Document document, int change) {
        try {
            String xml = new AppWizardXmlDOMSerializer().serialize(document);
            this.undoManager.addVersion(getProjectFilepath(), xml, change);
            new File(getProjectFilepath()).getParentFile().mkdirs();
            saveXml(xml);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveXml(String xml) throws IOException {
        FileWriter writer = new FileWriter(getProjectFilepath());
        writer.write(xml);
        writer.close();
    }

    @Override
    protected int inflateContentView() {
        setContentView(R.layout.appwizard);
        return R.id.appwizardContentContainer;
    }

    @Override
    protected Fragment createSectionFragment(AppWizardProject.AppFragment section) {
        return AppWizardDesignFragment.create(section);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.undoManager != null) {
            this.undoManager.removeListener(this);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(getPackageName(), "onCreate()");
        this.undoManager = new UndoManager();
        this.undoManager.addListener(this);
        if (savedInstanceState != null) {
            this.undoManager.load(savedInstanceState);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.undoManager != null) {
            this.undoManager.save(outState);
        }
    }

    public boolean isEditMode() {
        return getSharedPreferences(APP_WIZARD_SETTINGS, 0).getBoolean(PREF_APPWIZARD_EDITMODE, true);
    }

    public void setEditMode(boolean editMode) {
        SharedPreferences.Editor editor = getSharedPreferences(APP_WIZARD_SETTINGS, 0).edit();
        editor.putBoolean(PREF_APPWIZARD_EDITMODE, editMode);
        editor.apply();
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof AppWizardDesignFragment) {
                ((AppWizardDesignFragment) f).refreshEditMode();
            }
        }
        refreshButtons();
    }

    private void setEditListeners() {
        refreshButtons();
        findViewById(R.id.appwizardModeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View p1) {
                setEditMode(!isEditMode());
            }
        });
        findViewById(R.id.appwizardEditButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.showProperties();
            }
        });
        findViewById(R.id.appwizardUndoButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoManager.undo();
            }
        });
        findViewById(R.id.appwizardRedoButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoManager.redo();
            }
        });
    }

    @SuppressLint("WrongConstant")
    private void refreshButtons() {
        AppCompatImageView modeButton = (AppCompatImageView) findViewById(R.id.appwizardModeButton);
        if (isEditMode()) {
            modeButton.setImageResource(R.drawable.round_edit_24);
        } else {
            modeButton.setImageResource(R.drawable.round_image_24);
        }
        findViewById(R.id.appwizardEditButtonLayout).setVisibility(isEditMode() ? 0 : 8);
        findViewById(R.id.appwizardUndoButton).setEnabled(this.undoManager.canUndo());
        findViewById(R.id.appwizardRedoButton).setEnabled(this.undoManager.canRedo());
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        return MessageBox.onCreateDialog(this, id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public void refreshContent() {
        super.refreshContent();
        this.isInitialized = true;
        setEditListeners();
    }

    public UndoManager getUndoManager() {
        return this.undoManager;
    }
}
