I) Introduction

  Ce programme simule le fonctionnement d'une compagnie de taxis avec le Framework Jade.
  Plus précisément, cela va simuler les communications entre des taxis et leurs clients.
  La compagnie est composée de X taxis, voyageant à travers une ville. 
  Y voyageurs ont besoin d’effectuer un trajet. 
  Le service client est informé des positions des taxis libres travaillant dans la zone 
  du client et ne transmet les demandes des clients qu’aux taxis qui sont assez proches
  de lui. Puis les taxis intéressés répondent directement aux clients afin qu’ils 
  puissent choisir leur taxi en fonction de la réputation du taxi. Le client se 
  souvient des taxis qu’il a déjà pris et peut ou non le reprendre si le service lui a 
  convenu. Il existe aussi un système de réputation par potins, qui change la 
  réputation d’autres taxis pour un client lorsque celui-ci voyage.

II) Prérequis système

  Le programme a été compilé sur Windows mais il est possible de le faire fonctionner
  sur d'autre système d'exploitation (cela dépend des compatibilité Java).
  Il est nécessaire d'avoir Java d'installé sur son ordinateur, ainsi que Jade.

|||) Génération [et installation]

  Si besoin de générer, il faut compilé les .java à l'aide de la commande "javac".

|V) Utilisation

    Pour utiliser notre application, il faut lancer l'agent "NuberHost".
	Il est possible de lancer cet agent depuis un terminal avec cette commande:
	java jade.Boot -agents host:projet_jade.NuberHost
	Ou via l'interface graphique de jade ("java jade.Boot -GUI" puis créer un nouvel agent).
	Une fois l'agent lancé, cela affiche une interface graphique qui vous permet de créer 
	un nombre définie d'agents "Taxi" et d'agents "Client". Une fois le nombre séléctionné,
	on peut démarrer le tout en appuyant sur "Start" (des commentaires s'afficheront dans
	le terminal où on a lancer l'agent hôte indiquant ce qui se passe) ou arréter le tout
	en appuyant sur "Stop".
