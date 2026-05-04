package wikigen;

import arc.Core;
import arc.files.Fi;

import java.util.Locale;

/** General configuration. */
public class Config{
    public static final String repo = "wiki";
    public static final String language = System.getProperty("wikigen.lang", System.getenv().getOrDefault("WIKIGEN_LANG", "en"));
    public static final boolean chinese = language.toLowerCase(Locale.ROOT).startsWith("zh");
    public static final Fi outDirectory = Core.files.local("../../../Mindustry-Wiki-Generator/output/");
    public static final Fi rootDirectory = Core.files.local("../../../Mindustry-Wiki-Generator/");
    public static final Fi srcDirectory = Core.files.local("../../../Mindustry/core/src");
    public static final Fi baseDocsDirectory = Core.files.local("../../../" + repo + "/docs");
    public static final Fi docsDirectory = Core.files.local("../../../" + repo + "/" + (chinese ? "docs_zh" : "docs"));
    public static final Fi docsOutDirectory = Core.files.local("../../../" + repo + "/docs_out");
    public static final Fi imageDirectory = outDirectory.child("images");
    public static final Fi fileOutDirectory = docsOutDirectory;
    public static final Fi templatesDirectory = rootDirectory.child(chinese ? "templates_zh" : "templates");

    public static String tr(String english, String zhCN){
        return chinese ? zhCN : english;
    }

    public static Locale locale(){
        if(language.contains("_")){
            String[] split = language.split("_", 2);
            return new Locale(split[0], split[1]);
        }

        return new Locale(language);
    }
}
