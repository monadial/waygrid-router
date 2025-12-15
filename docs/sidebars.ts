import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docsSidebar: [
    'intro',
    {
      type: 'category',
      label: 'Traversal System',
      link: {
        type: 'doc',
        id: 'traversal/overview',
      },
      items: [
        'traversal/dag-model',
        'traversal/node-types',
        'traversal/state-machine',
        'traversal/signals-effects',
        'traversal/state-management',
        'traversal/examples',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api/json-schema',
        'api/error-codes',
      ],
    },
  ],
};

export default sidebars;
