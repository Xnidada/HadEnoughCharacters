package me.towdium.hecharacters;

import me.towdium.hecharacters.util.Match;
import mezz.jei.api.IAdvancedSearchRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.search.ISearchIndex;
import mezz.jei.api.search.ISearchIndexFactory;

@JEIPlugin
public class HechSearchPlugin implements IModPlugin {

    @Override
    public void registerAdvancedSearch(IAdvancedSearchRegistry registry) {
        if (HechConfig.enableHEI) {
            registry.replaceIndex(new ISearchIndexFactory() {
                @Override
                public <T> ISearchIndex<T> createSearchIndex() {
                    return new Match.FakeTree<>();
                }
            });
        }
    }
}
