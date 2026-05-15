package io.rcw.vibemine.ai.plugin.schema;
/*
    schema:
{
   "name":"tree_wand",
   "description":"Adds a wand that draws glowing text where the player looks.",
   "version":1,
   "globals":"(function(ctx) { const MAX_RANGE = 128; return { MAX_RANGE }; })",
   "events":[
      {
         "event":"block_break",
         "code":"(function(ctx, event, state) { // do stuff })"
      }
   ],
   "commands":[
      {
         "label":"test_command",
         "permission":"vibe.test",
         "code":"(function(ctx, sender, args, state) { // do stuff  })"
      }
   ]
}
     */


import java.util.List;

public record VibedPluginSchema(String name, String description, String globals, int version, List<Command> commands, List<Event> events) {
}
