Architectury Templates can be used for setting your own Architectury projects and converting your existing projects to Architectury projects.

The following templates are offered in [Creative Commons Zero v1.0 Universal](https://github.com/architectury/architectury-example-mod/blob/1.17.1/LICENSE).

Architectury API is added to the templates by default, there are instructions on how to remove them within the buildscripts, depending on the API is **not required** to use the Architectury toolchain.

## How to choose a template

#### Mixin or Not?

Mixins is a base game modification system available on both Forge and Fabric, Forge modders may be less familiar with this as Forge itself usually provides everything you need. This is not the case with Fabric. A lot of the events and hooks are not available on Fabric (Architectury API does reimplement some), and most of the times you will need to hook it up yourselves.

The Fabric Wiki's [Introduction to Mixins](https://fabricmc.net/wiki/tutorial:mixin_introduction) is a great resource to start out. However, you might not need Mixins if your mod is something simple like adding a few blocks and items, Fabric API provides the essentials for that.

#### I want to mod for Quilt?

There are currently three variants of the Architectury Templates:

| Targets                  | Description                                                  |
| ------------------------ | ------------------------------------------------------------ |
| Forge                    | The Forge sub-project used to call Forge-specific code, and handles Forge mod init.<br />You will also run Forge on this sub-project. |
| Fabric-Like Intermediary | The Fabric-like binding project used to call Fabric APIs, this can be used to call code that is unspecific to both Fabric and Quilt, such as hooks that will apply to both Fabric and Quilt.<br />This sub-project is **not executable**. |
| Fabric                   | The Fabric sub-project used to call Fabric-specific code, and handles Fabric mod init.<br />You will also run Fabric on this sub-project. |
| Quilt                    | The Quilt sub-project used to call Quilt-specific code, and handled Quilt mod init.<br />You will also run Quilt on this sub-project. |



| Variants                                  | Forge | Fabric-Like Intermediary | Fabric | Quilt |
| :---------------------------------------- | :---: | :----------------------: | :----: | :---: |
| Forge                                     |   ✔   |           N/A            |   ✖    |   ✖   |
| Forge-Fabric                              |   ✔   |           N/A            |   ✔    |   ✖   |
| Forge-Quilt                               |   ✔   |           N/A            |   ✖    |   ✔   |
| Forge-Fabric-Quilt<br />(Not Recommended) |   ✔   |            ✖             |   ✔    |   ✔   |
| Forge-Fabric-Likes                        |   ✔   |            ✔             |   ✔    |   ✔   |



## How to setup the template

Please read this documentation: [Getting Started with Architectury](https://docs.architectury.dev/plugin/get_started).



**Discord:** [https://discord.gg/C2RdJDpRBP](https://discord.gg/C2RdJDpRBP)
