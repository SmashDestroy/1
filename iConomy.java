package com.iCo6;

import com.iCo6.IO.Database;
import com.iCo6.IO.Database.Type;
import com.iCo6.IO.exceptions.MissingDriver;
import com.iCo6.command.Handler;
import com.iCo6.command.Parser;
import com.iCo6.command.exceptions.InvalidUsage;
import com.iCo6.handlers.Create;
import com.iCo6.handlers.Empty;
import com.iCo6.handlers.Give;
import com.iCo6.handlers.Help;
import com.iCo6.handlers.Money;
import com.iCo6.handlers.Payment;
import com.iCo6.handlers.Purge;
import com.iCo6.handlers.Remove;
import com.iCo6.handlers.Set;
import com.iCo6.handlers.Status;
import com.iCo6.handlers.Take;
import com.iCo6.handlers.Top;
import com.iCo6.listeners.players;
import com.iCo6.system.Account;
import com.iCo6.system.Accounts;
import com.iCo6.system.Holdings;
import com.iCo6.system.Interest;
import com.iCo6.system.Queried;
import com.iCo6.util.Common;
import com.iCo6.util.Messaging;
import com.iCo6.util.Template;
import com.iCo6.util.Thrun;
import com.iCo6.util.org.apache.commons.dbutils.DbUtils;
import com.iCo6.util.org.apache.commons.dbutils.QueryRunner;
import com.iCo6.util.org.apache.commons.dbutils.ResultSetHandler;
import com.iCo6.util.wget;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Timer;
import jline.ConsoleReader;
import jline.Terminal;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.h2.jdbcx.JdbcConnectionPool;

public class iConomy
  extends JavaPlugin
{
  public PluginDescriptionFile info;
  public PluginManager manager;
  private static Accounts Accounts = new Accounts();
  public Parser Commands = new Parser();
  public Permissions Permissions;
  private boolean testedPermissions = false;
  public static boolean TerminalSupport = false;
  public static File directory;
  public static Database Database;
  public static Server Server;
  public static Template Template;
  public static Timer Interest;
  private JdbcConnectionPool h2pool;
  
  public void onEnable()
  {
    long startTime = System.nanoTime();
    long endTime;
    try
    {
      Locale.setDefault(Locale.US);
      
      Server = getServer();
      if (getServer().getServerName().equalsIgnoreCase("craftbukkit")) {
        TerminalSupport = ((CraftServer)getServer()).getReader().getTerminal().isANSISupported();
      }
      this.info = getDescription();
      
      directory = getDataFolder();
      if (!directory.exists()) {
        directory.mkdir();
      }
      Common.extract(new String[] { "Config.yml", "Template.yml" });
      
      Constants.load(new File(directory, "Config.yml"));
      
      Template = new Template(directory.getPath(), "Template.yml");
      
      LinkedHashMap<String, String> nodes = new LinkedHashMap();
      nodes.put("top.opening", "<green>-----[ <yellow>Список богачей <green>]-----");
      nodes.put("top.item", "<white>+i. <green>+name <gray>- <white>+amount");
      try
      {
        Template.update(nodes);
      }
      catch (IOException ex)
      {
        System.out.println(ex.getMessage());
      }
      Database.Type type = Database.getType(Constants.Nodes.DatabaseType.toString());
      if ((!type.equals(Database.Type.InventoryDB)) && (!type.equals(Database.Type.MiniDB)))
      {
        Constants.Drivers driver = null;
        switch (type)
        {
        case H2DB: 
          driver = Constants.Drivers.H2; break;
        case MySQL: 
          driver = Constants.Drivers.MySQL; break;
        case SQLite: 
          driver = Constants.Drivers.SQLite; break;
        case Postgre: 
          driver = Constants.Drivers.Postgre;
        }
        if ((driver != null) && 
          (!new File("lib", driver.getFilename()).exists()))
        {
          System.out.println("[iConomy] Downloading " + driver.getFilename() + "...");
          wget.fetch(driver.getUrl(), driver.getFilename());
          System.out.println("[iConomy] Finished Downloading.");
        }
      }
      this.Commands.add("/money +name", new Money(this));
      this.Commands.setPermission("money", "iConomy.holdings");
      this.Commands.setPermission("money+", "iConomy.holdings.others");
      this.Commands.setHelp("money", new String[] { "", "Проверить свой баланс." });
      this.Commands.setHelp("money+", new String[] { " [имя]", "Проверить баланс игрока." });
      
      this.Commands.add("/money -h|?|help +command", new Help(this));
      this.Commands.setPermission("help", "iConomy.help");
      this.Commands.setHelp("help", new String[] { " (команда)", "Информация о команде." });
      
      this.Commands.add("/money -t|top", new Top(this));
      this.Commands.setPermission("top", "iConomy.top");
      this.Commands.setHelp("top", new String[] { "", "Посмотреть тор игроков по сабжам." });
      
      this.Commands.add("/money -p|pay +name +amount:empty", new Payment(this));
      this.Commands.setPermission("pay", "iConomy.payment");
      this.Commands.setHelp("pay", new String[] { " [имя] [кол-во]", "Перевод сабжей." });
      
      this.Commands.add("/money -c|create +name", new Create(this));
      this.Commands.setPermission("create", "iConomy.accounts.create");
      this.Commands.setHelp("create", new String[] { " [имя]", "Создание аккаунт iCo." });
      
      this.Commands.add("/money -r|remove +name", new Remove(this));
      this.Commands.setPermission("remove", "iConomy.accounts.remove");
      this.Commands.setHelp("remove", new String[] { " [имя]", "Удаление аккаунта." });
      
      this.Commands.add("/money -g|give +name +amount:empty", new Give(this));
      this.Commands.setPermission("give", "iConomy.accounts.give");
      this.Commands.setHelp("give", new String[] { " [имя] [кол-во]", "Выдать сабжи." });
      
      this.Commands.add("/money -t|take +name +amount:empty", new Take(this));
      this.Commands.setPermission("take", "iConomy.accounts.take");
      this.Commands.setHelp("take", new String[] { " [имя] [кол-во]", "Забрать сабжи." });
      
      this.Commands.add("/money -s|set +name +amount:empty", new Set(this));
      this.Commands.setPermission("set", "iConomy.accounts.set");
      this.Commands.setHelp("set", new String[] { " [имя] [кол-во]", "Указать кол-во сабжей на счету." });
      
      this.Commands.add("/money -u|status +name +status:empty", new Status(this));
      this.Commands.setPermission("status", "iConomy.accounts.status");
      this.Commands.setPermission("status+", "iConomy.accounts.status.set");
      this.Commands.setHelp("status", new String[] { " [имя] (статус)", "Проверка/Установка статуса аккаунта." });
      
      this.Commands.add("/money -x|purge", new Purge(this));
      this.Commands.setPermission("purge", "iConomy.accounts.purge");
      this.Commands.setHelp("purge", new String[] { "", "Очистка всех счетов до первоначального значения." });
      
      this.Commands.add("/money -e|empty", new Empty(this));
      this.Commands.setPermission("empty", "iConomy.accounts.empty");
      this.Commands.setHelp("empty", new String[] { "", "Удалить базу данных об аккаунтах." });
      try
      {
        Database = new Database(Constants.Nodes.DatabaseType.toString(), Constants.Nodes.DatabaseUrl.toString(), Constants.Nodes.DatabaseUsername.toString(), Constants.Nodes.DatabasePassword.toString());
        if (Database.isSQL())
        {
          if (!Database.tableExists(Constants.Nodes.DatabaseTable.toString()))
          {
            String SQL = Common.resourceToString("SQL/Core/Create-Table-" + Database.getType().toString().toLowerCase() + ".sql");
            SQL = String.format(SQL, new Object[] { Constants.Nodes.DatabaseTable.getValue() });
            try
            {
              QueryRunner run = new QueryRunner();
              Connection c = Database.getConnection();
              try
              {
                run.update(c, SQL);
              }
              catch (SQLException ex)
              {
                System.out.println("[iConomy] Error creating database: " + ex);
              }
              finally
              {
                DbUtils.close(c);
              }
            }
            catch (SQLException ex)
            {
              System.out.println("[iConomy] Database Error: " + ex);
            }
          }
        }
        else {
          onConversion();
        }
      }
      catch (MissingDriver ex)
      {
        System.out.println(ex.getMessage());
      }
      getServer().getPluginManager().registerEvents(new players(), this);
    }
    finally
    {
      endTime = System.nanoTime();
    }
    if (Constants.Nodes.Interest.getBoolean().booleanValue()) {
      Thrun.init(new Runnable()
      {
        public void run()
        {
          long time = Constants.Nodes.InterestTime.getLong().longValue() * 1000L;
          
          iConomy.Interest = new Timer();
          iConomy.Interest.scheduleAtFixedRate(new Interest(iConomy.this.getDataFolder().getPath()), time, time);
        }
      });
    }
    if (Constants.Nodes.Purging.getBoolean().booleanValue()) {
      Thrun.init(new Runnable()
      {
        public void run()
        {
          Queried.purgeDatabase();
          System.out.println("[" + iConomy.this.info.getName() + " - " + Constants.Nodes.CodeName.toString() + "] Purged accounts with default balance.");
        }
      });
    }
    long duration = endTime - startTime;
    
    System.out.println("[" + this.info.getName() + " - " + Constants.Nodes.CodeName.toString() + "] Enabled (" + Common.readableProfile(duration) + ")");
    System.out.println("[" + this.info.getName() + "] Rare Version!");
  }
  
  public void onDisable()
  {
    String name = this.info.getName();
    System.out.println("[" + name + "] Closing general data...");
    
    long startTime = System.nanoTime();
    long endTime;
    try
    {
      this.info = null;
      Server = null;
      this.manager = null;
      Accounts = null;
      this.Commands = null;
      Database = null;
      Template = null;
      if (Interest != null)
      {
        Interest.cancel();
        Interest.purge();
        Interest = null;
      }
      TerminalSupport = false;
    }
    finally
    {
      endTime = System.nanoTime();
    }
    long duration = endTime - startTime;
    
    System.out.println("[" + name + "] Disabled. (" + Common.readableProfile(duration) + ")");
  }
  
  public boolean onConversion()
  {
    if (!Constants.Nodes.Convert.getBoolean().booleanValue()) {
      return false;
    }
    Thrun.init(new Runnable()
    {
      public void run()
      {
        String from = Constants.Nodes.ConvertFrom.toString();
        String table = Constants.Nodes.ConvertTable.toString();
        String username = Constants.Nodes.ConvertUsername.toString();
        String password = Constants.Nodes.ConvertPassword.toString();
        String url = Constants.Nodes.ConvertURL.toString();
        if (!Common.matches(from, new String[] { "h2", "h2db", "h2sql", "mysql", "mysqldb" })) {
          return;
        }
        String driver = "";String dsn = "";
        if (Common.matches(from, new String[] { "sqlite", "h2", "h2sql", "h2db" }))
        {
          driver = "org.h2.Driver";
          dsn = "jdbc:h2:" + iConomy.directory + File.separator + table + ";AUTO_RECONNECT=TRUE";
          username = "sa";
          password = "sa";
        }
        else if (Common.matches(from, new String[] { "mysql", "mysqldb" }))
        {
          driver = "com.mysql.jdbc.Driver";
          dsn = url + "/" + table;
        }
        if (!DbUtils.loadDriver(driver))
        {
          System.out.println("Please make sure the " + from + " driver library jar exists.");
          
          return;
        }
        Connection old = null;
        try
        {
          old = (username.isEmpty()) && (password.isEmpty()) ? DriverManager.getConnection(url) : DriverManager.getConnection(url, username, password);
        }
        catch (SQLException ex)
        {
          System.out.println(ex);
          return;
        }
        QueryRunner run = new QueryRunner();
        try
        {
          try
          {
            run.query(old, "SELECT * FROM " + table, new ResultSetHandler()
            {
              public Object handle(ResultSet rs)
                throws SQLException
              {
                Account current = null;
                Boolean next = Boolean.valueOf(rs.next());
                if (next.booleanValue()) {
                  if (iConomy.Accounts.exists(rs.getString("username"))) {
                    current = iConomy.Accounts.get(rs.getString("username"));
                  } else {
                    iConomy.Accounts.create(rs.getString("username"), Double.valueOf(rs.getDouble("balance")));
                  }
                }
                if (current != null) {
                  current.getHoldings().setBalance(rs.getDouble("balance"));
                }
                if ((next.booleanValue()) && 
                  (iConomy.Accounts.exists(rs.getString("username"))) && 
                  (rs.getBoolean("hidden"))) {
                  iConomy.Accounts.get(rs.getString("username")).setStatus(1);
                }
                return Boolean.valueOf(true);
              }
            });
          }
          catch (SQLException ex)
          {
            System.out.println("[iConomy] Error issueing SQL query: " + ex);
          }
          finally
          {
            DbUtils.close(old);
          }
        }
        catch (SQLException ex)
        {
          System.out.println("[iConomy] Database Error: " + ex);
        }
        System.out.println("[iConomy] Conversion complete. Please update your configuration, change convert to false!");
      }
    });
    return false;
  }
  
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
  {
    Handler handler = this.Commands.getHandler(command.getName());
    String split = "/" + command.getName().toLowerCase();
    for (int i = 0; i < args.length; i++) {
      split = split + " " + args[i];
    }
    Messaging.save(sender);
    this.Commands.save(split);
    this.Commands.parse();
    if (this.Commands.getHandler() != null) {
      handler = this.Commands.getHandler();
    }
    if (handler == null) {
      return false;
    }
    try
    {
      return handler.perform(sender, this.Commands.getArguments());
    }
    catch (InvalidUsage ex)
    {
      Messaging.send(sender, ex.getMessage());
    }
    return false;
  }
  
  public boolean hasPermissions(CommandSender sender, String command)
  {
    if ((sender instanceof Player))
    {
      Player player = (Player)sender;
      if (player == null)
      {
        System.out.println("[iConomy] Cannot execute command with false player");
        return false;
      }
      if (this.Commands.hasPermission(command))
      {
        String node = this.Commands.getPermission(command);
        if (node == null) {
          return true;
        }
        if ((this.Permissions == null) && 
          (!this.testedPermissions))
        {
          Plugin Perms = Server.getPluginManager().getPlugin("Permissions");
          if ((Perms != null) && 
            (Perms.isEnabled()))
          {
            this.Permissions = ((Permissions)Perms);
            System.out.println("[iConomy] hooked into Permissions.");
          }
          this.testedPermissions = true;
        }
        if (this.Permissions != null)
        {
          if ((Permissions.Security.permission(player, node)) || (Permissions.Security.permission(player, node.toLowerCase()))) {
            return true;
          }
          return false;
        }
        try
        {
          Permission perm = new Permission(node);
          if ((player.hasPermission(perm)) || (player.hasPermission(node)) || (player.hasPermission(node.toLowerCase()))) {
            return true;
          }
          return false;
        }
        catch (Exception e)
        {
          return player.isOp();
        }
      }
    }
    return true;
  }
  
  public static String format(String account)
  {
    return Accounts.get(account).getHoldings().toString();
  }
  
  public static String format(double amount)
  {
    DecimalFormat formatter = new DecimalFormat("#,##0.00");
    String formatted = formatter.format(amount);
    if (formatted.endsWith(".")) {
      formatted = formatted.substring(0, formatted.length() - 1);
    }
    return Common.formatted(formatted, Constants.Nodes.Major.getStringList(), Constants.Nodes.Minor.getStringList());
  }
}