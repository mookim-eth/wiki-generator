package wikigen;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.JsonWriter.*;
import arc.util.serialization.*;
import arc.util.serialization.Jval.*;
import com.github.javaparser.*;
import com.github.javaparser.ast.body.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.server.*;
import mindustry.type.*;
import mindustry.world.blocks.legacy.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;
import org.reflections.*;
import wikigen.Generator.*;

import java.util.*;

import static arc.util.Log.*;

/** Generates and replaces variables in markdown files. */
@SuppressWarnings("unchecked")
public class VarGenerator{

    public ObjectMap<String, Object> makeVariables() throws Exception{
        var out = new ObjectMap<String, Object>();

        out.put("sounds", Seq.with(Sounds.class.getFields()).toString(" ", f -> "`" + f.getName() + "`"));
        out.put("contentTypes", Seq.with(ContentType.all).select(c -> !c.name().contains("UNUSED")).toString(" ", c -> "`" + c.name() + "`"));
        out.put("bundles", Seq.with(Core.files.local("locales").readString().split("\n")).toString(" ", c -> "`" + c + "`"));
        out.put("blockGroups", Seq.with(BlockGroup.values()).toString("\n", g -> "- `" + g.name() + "`"));
        out.put("buildVisibilities", Seq.with(BuildVisibility.class.getFields()).toString("\n", g -> "- `" + g.getName() + "`"));

        //create dummy server to scrape its commands
        var cont = new ServerControl(null){
           @Override
           public void setup(String[] args){
               registerCommands();
           }
        };

        out.put("serverCommands", cont.handler.getCommandList().toString("\n", command -> "- `" + command.text + (command.paramText.isEmpty() ? "" : " ") + command.paramText + "`: *" + serverCommandDescription(command.text, command.description) + "*"));
        out.put("serverConfigs", Seq.with(Administration.Config.all).toString("\n", conf -> "- `" + conf.name + "`: *" + serverConfigDescription(conf.name, conf.description) + "*"));

        Http.get("https://api.github.com/repos/Anuken/Mindustry/releases").header("Accept", "application/vnd.github.v3+json").block(response -> {
            Jval json = Jval.read(response.getResultAsString());
            String latestRelease = json.asArray().first().getString("tag_name").substring(1);
            out.put("latestRelease", latestRelease);
            String latestReleaseLink = json.asArray().first().getString("html_url");
            out.put("latestReleaseLink", latestReleaseLink);
        });

        //TODO wrong
        out.put("allTypes", genTypes());

        return out;
    }

    Set<Class> fetchTypes(String pack, Class sub){
        var reflections = new Reflections(pack);
        var allClasses = reflections.getSubTypesOf(sub);
        allClasses.add(sub);
        return allClasses;
    }

    String fetchFields(Class type){
        return Seq.with(type.getFields()).toString(" ", f -> "`" + f.getName() + "`");
    }

    private String serverCommandDescription(String name, String fallback){
        if(!Config.chinese) return fallback;
        return switch(name){
            case "help" -> "显示命令列表，或查看某个命令的帮助。";
            case "version" -> "显示服务器版本信息。";
            case "exit" -> "退出服务器程序。";
            case "stop" -> "停止托管服务器。";
            case "host" -> "开启服务器；未指定时默认使用生存模式和随机地图。";
            case "maps" -> "显示可用地图；默认只显示自定义地图。";
            case "reloadpatches" -> "从磁盘重新加载所有补丁文件。";
            case "reloadmaps" -> "从磁盘重新加载所有地图。";
            case "status" -> "显示服务器状态。";
            case "mods" -> "显示所有已加载的模组。";
            case "mod" -> "显示某个已加载插件的信息。";
            case "js" -> "运行任意 JavaScript。";
            case "say" -> "向所有玩家发送消息。";
            case "pause" -> "暂停或继续游戏。";
            case "rules" -> "列出、移除或添加全局规则；这些规则会应用到所有地图。";
            case "fillitems" -> "用物品填满核心。";
            case "playerlimit" -> "设置服务器玩家人数上限。";
            case "config" -> "配置服务器设置。";
            case "subnet-ban" -> "封禁一个子网；会拒绝所有 IP 以指定字符串开头的连接。";
            case "name-ban" -> "按不区分大小写的正则表达式封禁名称。";
            case "whitelist" -> "使用玩家 ID 将玩家加入或移出白名单。";
            case "shuffle" -> "设置地图轮换模式。";
            case "nextmap" -> "设置游戏结束后要游玩的下一张地图，并覆盖随机轮换。";
            case "kick" -> "按名称踢出玩家。";
            case "ban" -> "封禁玩家。";
            case "bans" -> "列出所有被封禁的 IP 和 ID。";
            case "unban" -> "通过 IP 或 ID 完全解除封禁。";
            case "pardon" -> "通过 ID 赦免被投票踢出的玩家，允许其再次加入。";
            case "admin" -> "将在线用户设为管理员，或移除管理员权限。";
            case "admins" -> "列出所有管理员。";
            case "players" -> "列出当前游戏中的所有玩家。";
            case "runwave" -> "触发下一波敌人。";
            case "loadautosave" -> "加载最近一次自动保存。";
            case "load" -> "从存档槽加载存档。";
            case "save" -> "将游戏状态保存到存档槽。";
            case "saves" -> "列出存档目录中的所有存档。";
            case "gameover" -> "强制结束游戏。";
            case "info" -> "查找玩家信息；也可以检查玩家曾用过的所有名称或 IP。";
            case "search" -> "搜索曾使用过某段名称的玩家。";
            case "gc" -> "触发垃圾回收；仅用于测试。";
            case "yes" -> "执行上一次建议的错误命令。";
            case "dos-ban" -> "添加或移除 DOS 封禁。";
            default -> fallback;
        };
    }

    private String serverConfigDescription(String name, String fallback){
        if(!Config.chinese) return fallback;
        return switch(name){
            case "name" -> "客户端上显示的服务器名称。";
            case "desc" -> "显示在服务器名称下方的说明，最多 100 个字符。";
            case "port" -> "用于托管的端口。";
            case "autoUpdate" -> "当新的 bleeding-edge 更新到来时，是否自动更新并退出。";
            case "showConnectMessages" -> "是否显示连接/断开连接消息。";
            case "enableVotekick" -> "是否启用投票踢人。";
            case "startCommands" -> "启动时运行的命令；应使用逗号分隔。";
            case "logging" -> "是否将所有内容记录到文件。";
            case "strict" -> "是否启用严格模式；会校正位置并防止重复 UUID。";
            case "antiSpam" -> "是否自动踢出并限制刷屏者。";
            case "interactRateWindow" -> "方块交互速率限制窗口，单位为秒。";
            case "interactRateLimit" -> "方块交互速率限制。";
            case "interactRateKick" -> "玩家在限制窗口内交互多少次后会被踢出。";
            case "messageRateLimit" -> "消息速率限制，单位为秒；0 表示禁用。";
            case "messageSpamKick" -> "玩家在冷却前发送多少条消息后会被踢出；0 表示禁用。";
            case "packetSpamLimit" -> "3 秒内收到的数据包数量上限，超过会加入黑名单并踢出。";
            case "chatSpamLimit" -> "2 秒内收到的聊天数据包数量上限，超过会加入黑名单并踢出；这不同于速率限制。";
            case "socketInput" -> "是否允许本地应用通过本地 TCP 套接字控制此服务器。";
            case "socketInputPort" -> "套接字输入使用的端口。";
            case "socketInputAddress" -> "套接字输入绑定的地址。";
            case "allowCustomClients" -> "是否允许自定义客户端连接。";
            case "whitelist" -> "是否使用白名单。";
            case "motd" -> "玩家连接时显示的消息。";
            case "autosave" -> "游玩时是否定期自动保存地图。";
            case "autosaveAmount" -> "自动存档的最大数量；较旧的存档会被替换。";
            case "autosaveSpacing" -> "自动保存间隔，单位为秒。";
            case "debug" -> "启用调试日志。";
            case "snapshotInterval" -> "客户端实体快照间隔，单位为毫秒。";
            case "autoPause" -> "无人在线时游戏是否暂停。";
            case "roundExtraTime" -> "游戏结束后加载新地图前的等待时间，单位为秒。";
            case "maxLogLength" -> "日志文件最大大小，单位为字节。";
            case "logCommands" -> "是否记录玩家命令。";
            default -> fallback;
        };
    }

    public String genTypes() throws Exception{
        var allClasses = fetchTypes("mindustry", MappableContent.class);
        allClasses.addAll(fetchTypes("mindustry.entities.effect", Effect.class));
        allClasses.addAll(fetchTypes("mindustry.entities.abilities", Ability.class));
        allClasses.addAll(fetchTypes("mindustry.entities.bullet", BulletType.class));
        allClasses.addAll(fetchTypes("mindustry.type.weapons", Weapon.class));
        allClasses.addAll(fetchTypes("mindustry.type.weather", Weather.class));
        allClasses.addAll(fetchTypes("mindustry.world.draw", DrawBlock.class));
        allClasses.add(Weapon.class);
        allClasses.add(SectorPreset.class);

        var parser = new JavaParser();
        var builtIns = StringMap.of(
        "effect", fetchFields(Fx.class),
        "bullet", fetchFields(Bullets.class),
        "status", fetchFields(StatusEffects.class)
        );

        class Ref{
            Class c;
            String type;
            Object instance;

            Ref(Class c, String type, Object instance){
                this.c = c;
                this.type = type;
                this.instance = instance;
            }
        }

        var refs = new Seq<Ref>();
        var counts = new ObjectIntMap<String>();
        var allContent = Seq.with(Vars.content.getContentMap()).<Content>flatten().select(o -> o.minfo.mod != null);

        for(var c : allClasses){
            if(c.isAnonymousClass() || c.isAnnotationPresent(Deprecated.class) || LegacyBlock.class.isAssignableFrom(c)) continue;

            Object instance = null;

            if(c == BulletType.class) instance = new BulletType();
            if(c == Ability.class) instance = new Ability(){};
            if(c == SectorPreset.class) instance = new SectorPreset("sectorName", "groundZero", Planets.serpulo, 5);

            if(instance == null){
                //skip non-string constructors
                try{
                    instance = c.getConstructor(String.class).newInstance(Strings.capitalize(c.getSimpleName()) + " Name");
                }catch(Exception ignored){
                    try{
                        var cons = c.getDeclaredConstructor();
                        cons.setAccessible(true);
                        instance = cons.newInstance();
                    }catch(Exception ignored2){
                        continue;
                    }
                }
            }

            //TODO garbage code?? need to list subclasses as well...
            String type = instance instanceof Content cont ? cont.getContentType().toString() : instance instanceof Effect ? "effect" : "zzz_other";
            counts.increment(type);

            refs.add(new Ref(c, type, instance));
        }

        refs.sort(((Comparator<Ref>)((a, b) -> -Boolean.compare(a.c.isAssignableFrom(b.c), b.c.isAssignableFrom(a.c)))).thenComparing(r -> r.type).thenComparing(f -> f.c.getSimpleName()));
        var generatedClasses = new ObjectSet<String>();
        refs.each(r -> generatedClasses.add(r.c.getSimpleName()));

        for(var ref : refs){
            var out = new StringBuilder();

            if(builtIns.containsKey(ref.type)){
                out.append(Config.tr("Built-in constants:", "内置常量：")).append("  \n\n").append(builtIns.get(ref.type)).append("  \n  ");
            }

            out.append("\n");

            var c = ref.c;
            var path = c.getCanonicalName().replace('.', '/') + ".java";
            var supclass = c.getSuperclass().getSimpleName();

            //pick JSON objects of approximately average length; long files are not used, as those tend to have too many long particle effects.
            //TODO better selection criteria
            float complexity = 0.5f;

            Object example = allContent.select(cont -> cont.getClass() == c && cont.minfo.sourceFile != null && cont.minfo.sourceFile.length() < 1024 * 5).sort(cont -> cont.minfo.mod.file.length()).getFrac(complexity);
            if(example == null){
                example = Generator.parsed.select(p -> p.object != null && p.object.getClass() == c).sort(p -> p.json.toJson(OutputType.json).length()).getFrac(complexity);
            }

            String exampleJson = example == null ? null : example instanceof Content cont ? cont.minfo.sourceFile.readString() : ((ParseRecord)example).json.toJson(OutputType.json);

            info("Parsing @@", path, example == null ? "" : " &lb(found example)&fr");

            out.append("## ").append(c.getSimpleName()).append("\n\n");

            //TODO do not link non-existent stuff
            out.append("*").append(Config.tr("extends", "继承自")).append(" ");
            if(generatedClasses.contains(supclass)){
                out.append("[").append(supclass).append("](").append(supclass).append(".md)");
            }else{
                out.append("`").append(supclass).append("`");
            }
            out.append("*\n\n");

            var cu = parser.parse(Config.srcDirectory.child(path).file()).getResult().orElseThrow();
            var typeDec = cu.getTypes().getFirst().orElseThrow();

            if(Config.chinese){
                out.append("此页列出 `").append(c.getSimpleName()).append("` 的可配置字段、类型和默认值。\n");
            }else if(typeDec.getJavadoc().isPresent()){
                out.append(typeDec.getJavadoc().get().toText()).append("\n");
            }

            boolean anyFields = false;

            var outf = new StringBuilder();

            outf.append(Config.tr("""
            |field|type|default|notes|
            |---|---|---|---|
            """, """
            |字段|类型|默认值|说明|
            |---|---|---|---|
            """));

            var members = typeDec.getMembers();
            if(members != null){
                for(var member : members){
                    if(member instanceof FieldDeclaration field){
                        if(field.isStatic() || !field.isPublic()) continue;

                        for(var variable : field.getVariables()){
                            var baseField = c.getField(variable.getNameAsString());
                            var value = baseField.get(ref.instance);
                            var initValue = variable.getInitializer().isEmpty() ? null : variable.getInitializer().get().toString();

                            //array init
                            if(initValue != null && initValue.equals("{}")){
                                initValue = "[]";
                            }

                            //special array init
                            if(initValue != null && initValue.contains("new") && initValue.contains("[") && initValue.contains("]")){
                                initValue = "[]";
                            }

                            if(value instanceof Seq){
                                initValue = value + "";
                            }

                            //assign to last, making sure it's not a number
                            if(initValue != null && initValue.contains(".") && !(baseField.getType().isArray())){
                                var split = initValue.split("\\.");
                                initValue = split[split.length - 1];
                            }

                            if(initValue != null && value instanceof Object[] o){
                                initValue = Arrays.toString(o);
                            }

                            if(initValue != null && value instanceof float[] f){
                                initValue = Arrays.toString(f);
                            }

                            //special overrides
                            if(value instanceof Color || value instanceof Vec2 || value instanceof Number){
                                initValue = String.valueOf(value);
                            }

                            //remove f suffix
                            if(variable.getTypeAsString().equals("float") && initValue != null && initValue.endsWith("f")){
                                initValue = initValue.substring(0, initValue.length() - 1);
                            }

                            //remove lambdas
                            if(initValue != null && initValue.contains("->")){
                                initValue = "{code}";
                            }

                            anyFields = true;
                            outf
                            .append("|").append(variable.getName())
                            .append("|").append(variable.getType().toString().replace("<", Config.tr(" of ", " 的 ")).replace(">", ""))
                            .append("|").append(initValue == null ? value : initValue)
                            .append("|").append(determineJavadoc(field, variable)).append("|\n");
                        }
                    }
                }
            }

            if(example != null){
                var read = Jval.read(exampleJson);

                //a single string is a terrible example.
                if(read.isObject()){
                    String json = Jval.read(exampleJson).toString(Jformat.hjson);

                    if(!json.trim().isEmpty()){
                        outf.append("\n#### ").append(Config.tr("Example", "示例"));
                        if(example instanceof UnlockableContent cont){
                            Log.info(cont.minfo.sourceFile.path());
                            String realPath = "https://github.com/BlueWolf3682/Exotic-Mod/tree/master" + cont.minfo.sourceFile.path().replace("Exotic-Mod-master", "");
                            outf.append(" ").append(" [(\"").append(cont.localizedName).append("\")](").append(realPath).append(")");
                        }
                        outf.append("\n");
                        outf.append("```\n");

                        outf.append(json);

                        outf.append("```\n");
                    }
                }
            }

            if(anyFields){
                out.append(outf);
            }

            out.append("\n\n");

            Config.outDirectory.child("Modding Classes").child(c.getSimpleName() + ".md").writeString(out.toString());
        }

        return ""; //TODO remove
    }

    private String determineJavadoc(FieldDeclaration field, VariableDeclarator variable){
        if(Config.chinese){
            return " ";
        }

        if(variable.getComment().isPresent()){
            return variable.getComment().get().getContent().replace("\n", " ");
        }else if(field.getJavadoc().isPresent()){
            return field.getJavadoc().get().toText().replace("\n", " ");
        }else{
            return " ";
        }

    }

    public void generate() throws Exception{
        var values = makeVariables();

        Config.docsOutDirectory.deleteDirectory();
        Config.docsOutDirectory.delete();
        Config.docsOutDirectory.mkdirs();

        if(Config.chinese){
            for(Fi file : Config.baseDocsDirectory.list()){
                file.copyTo(Config.docsOutDirectory);
            }
        }

        for(Fi file : Config.docsDirectory.list()){
            file.copyTo(Config.docsOutDirectory);
        }

        Config.docsOutDirectory.walk(f -> {
            if(f.extEquals("md")){
                StringBuilder template = new StringBuilder(f.readString());
                values.each((key, val) -> {
                    if(!Generator.str(val).isEmpty()){
                        Strings.replace(template, "$" + key, Generator.str(val));
                    }
                });
                f.writeString(Strings.join("\n", Seq.with(template.toString().split("\n")).select(s -> !s.contains("$"))));
            }
        });
    }
}
