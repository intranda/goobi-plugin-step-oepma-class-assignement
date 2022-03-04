package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.goobi.vocabulary.Field;
import org.goobi.vocabulary.VocabRecord;
import org.goobi.vocabulary.Vocabulary;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.VocabularyManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class OepmaClassAssignementStepPlugin implements IStepPluginVersion2 {
    
    @Getter
    private String title = "intranda_step_oepma_class_assignement";
    @Getter
    private Step step;
    private String vocabulary;
    private String classField;
    private String termsField;
    private String metadataTitle;
    private String metadataClass;
    private String returnPath;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
                
        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        vocabulary = myconfig.getString("vocabulary"); 
        classField = myconfig.getString("class"); 
        termsField = myconfig.getString("terms"); 
        metadataTitle = myconfig.getString("metadataTitle"); 
        metadataClass = myconfig.getString("metadataClass"); 
        
        log.info("OepmaClassAssignement step plugin initialized");
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_oepma_class_assignement.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }
    
    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }
    
    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successful = true;
        
        try {
            // read mets file
            Fileformat ff = step.getProzess().readMetadataFile();
            Prefs prefs = step.getProzess().getRegelsatz().getPreferences();
            DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
            DocStruct anchor = null;
            if (logical.getType().isAnchor()) {
                anchor = logical;
                logical = logical.getAllChildren().get(0);
            }
            
            // delete existing metadata of defined type
            List<Metadata> originalMetadata = new ArrayList<>();
            for (Metadata md : logical.getAllMetadata()) {
                if (md.getType().getName().equals(metadataClass)) {
                    originalMetadata.add(md);
                } 
            }
            for (Metadata metadata : originalMetadata) {
				logical.removeMetadata(metadata);
			}
            
            // find out all classes that shall be assigned
            String contentTitle = "";
            for (Metadata md : logical.getAllMetadata()) {
                if (md.getType().getName().equals(metadataTitle)) {
                	contentTitle = md.getValue();
                } 
            }

            Set <String> classesToAssign = new HashSet<String>();
            
            // load the vocabulary and all records
            Vocabulary vocab = VocabularyManager.getVocabularyByTitle(vocabulary);
            VocabularyManager.getAllRecords(vocab);
            
            // run through all records
            for (VocabRecord myRecord : vocab.getRecords()) {
            	List<Field> fields = myRecord.getFields();
            	String myClass = null;
            	String myTerms = null;
            	
            	// read class name and terms
            	for (Field field : fields) {
            		if (field.getLabel().equals(classField)) {
            			myClass = field.getValue();
            		}
            		if (field.getLabel().equals(termsField)) {
            			myTerms = field.getValue();
            		}
    			}
            	
            	// check if class matches
            	PatternMatcher pm = new PatternMatcher();
            	if (pm.match(myTerms, contentTitle)) {
            		classesToAssign.add(myClass);
            	}
            }
            
            
            //finally add all matching classes as new metadata
            for (String s : classesToAssign) {
            	Metadata md = new Metadata(prefs.getMetadataTypeByName(metadataClass));
            	md.setValue(s);
            	logical.addMetadata(md);            	
			}
            
            // save the mets file
            step.getProzess().writeMetadataFile(ff);
        } catch (ReadException | PreferencesException | WriteException | IOException | InterruptedException | SwapException | DAOException | MetadataTypeNotAllowedException e) {
            log.error(e);
        }
        
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }
}
