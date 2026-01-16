# EzCord Utils - IntelliJ Plugin

A powerful IntelliJ plugin to streamline Discord bot development with [EzCord](https://ezcord.readthedocs.io/en/latest/).

## ✨ Features

### 🔤 Language Key Features
- **Smart Language Key Autocomplete** - Intelligent suggestions for available language keys as you type
- **Quick Documentation** - Shows translations when hovering over language keys in real-time
- **One-Click Navigation** - Jump directly to language definitions in YAML files via gutter icons
- **Language Keys Tool Window** - View all language keys and translations in a dedicated tool window with detailed information

### ⚡ Live Templates
With the integrated Live Templates, you can quickly insert code snippets for py-cord and discord.py. Simply type the abbreviation and press `Tab`.

#### Available Templates:

| Abbreviation  | Description                  | Usage                                                 |
|---------------|------------------------------|-------------------------------------------------------|
| `ez-main`     | [py-cord] Main with ezcord   | Creates a Complete Main setup with ezcord integrated  |
| `ez-cog`      | [py-cord] Cog with ezcord    | Creates a Complete Cog setup with ezcord integrated   |
| `ez-button`   | [py-cord] Button with ezcord | Creates a discord.ui.Button with ezcord callback      |
| `ez-modal`    | [py-cord] Modal with ezcord  | Creates a discord.ui.DesignerModal with ezcord        |
| `ez-select`   | [py-cord] Select with ezcord | Creates a discord.ui.Select with ezcord callback      |
| `ezpy-main`   | [d.py] Main with ezcord      | Creates a Complete Main setup with ezcord integrated  |
| `ezpy-cog`    | [d.py] Cog with ezcord       | Creates a Complete Cog setup with ezcord integrated   |
| `ezpy-button` | [d.py] Button with ezcord    | Creates a discord.ui.Button with interaction callback |
| `ezpy-modal`  | [d.py] Modal with ezcord     | Creates a discord.ui.Modal with on_submit method      |
| `ezpy-select` | [d.py] Select with ezcord    | Creates a discord.ui.Select with interaction callback |

#### Example: `ez-main` Template

```python
import discord
import ezcord


class Bot(ezcord.Bot):
    def __init__(self):
        super().__init__(intents=discord.Intents.default(), language="en")

        self.load_cogs()
        self.add_help_command()
        self.add_status_changer(
            "Ezcord",
            discord.Game("on {guild_count} servers"),
        )


if __name__ == "__main__":
    bot = Bot()
    bot.run()
```

## 🚀 Installation

1. Open IntelliJ IDEA / PyCharm
2. Go to `Settings` → `Plugins`
3. Search for "EzCord Utils"
4. Click `Install`

Or download the plugin manually from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/29591-ezcord-utils).

## 📖 Usage

### Using Live Templates
1. Open a Python file
2. Type one of the template abbreviations (e.g. `ezcordbot`)
3. Press `Tab`
4. Navigate through the placeholders with `Tab` and fill in the values

### Using Language Keys
1. Configure the language folder in `Settings` → `Tools` → `EzCord Settings`
2. Start typing a language key (e.g., ctx.t("") or directly "general.test") in your Python code
3. Get automatic suggestions for available keys
4. Click the gutter icon to jump to the definition (or `Ctrl+Click` for fallback language)
