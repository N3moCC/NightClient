"""Regression contracts for setter accessors and projection argument injections."""
import unittest
from types import SimpleNamespace
from pathlib import Path
import sys
_TOOLS_DIR = Path(__file__).resolve().parents[1]
for _sub in ('', 'generators', 'matrix', 'checks', 'benchmarks', 'qa'):
    _p = str(_TOOLS_DIR / _sub if _sub else _TOOLS_DIR)
    if _p not in sys.path:
        sys.path.insert(0, _p)

from audit_compatibility import mixin_audit


class Resolver:
    def __init__(self):
        self.field={'name':'colorTexture','descriptor':'LGpuTexture;','access':4}
        self.render={'name':'render','descriptor':'()V','access':1}
        self.target=SimpleNamespace(methods=[self.render],code_refs=lambda _: [(10,'game/Projection','perspective','(FFFF)LMatrix;')])

    def get(self,name):
        return self.target if name=='game/Renderer' else None

    def find_member(self,owner,name,descriptor,field=False):
        return ('game/Renderer',self.field) if field and name=='colorTexture' else None


def fixture(descriptor,annotation,static=False):
    method={'name':'hook','descriptor':descriptor,'access':8 if static else 1,'attributes':[annotation]}
    return SimpleNamespace(name='test/StrictMixin',attributes=[{'annotation':'Lorg/spongepowered/asm/mixin/Mixin;','value':['Lgame/Renderer;']}],fields=[],methods=[method],annotations=lambda attrs:attrs)


class AuditRegression(unittest.TestCase):
    def audit(self,descriptor,annotation,static=False):
        errors=[];mixin_audit(fixture(descriptor,annotation,static),Resolver(),errors);return errors

    def test_matching_getter_and_setter_are_both_valid(self):
        annotation={'annotation':'Lorg/spongepowered/asm/mixin/gen/Accessor;','value':'colorTexture'}
        self.assertEqual([],self.audit('()LGpuTexture;',annotation))
        self.assertEqual([],self.audit('(LGpuTexture;)V',annotation))

    def test_accessor_interface_rejects_extra_methods_for_class_targets(self):
        annotation={'annotation':'Lorg/spongepowered/asm/mixin/gen/Accessor;','value':'colorTexture'}
        cls=fixture('()LGpuTexture;',annotation);cls.access=0x0201
        resolver=Resolver();resolver.target.access=1
        errors=[];mixin_audit(cls,resolver,errors);self.assertEqual([],errors)
        helper={'name':'helper','descriptor':'()LGpuTexture;','access':1,'attributes':[]}
        cls.methods.append(helper)
        errors=[];mixin_audit(cls,resolver,errors)
        self.assertTrue(any('not a pure accessor' in error for error in errors))
        helper['access']|=0x1000
        errors=[];mixin_audit(cls,resolver,errors);self.assertEqual([],errors)
        helper['access']=1;resolver.target.access|=0x0200
        errors=[];mixin_audit(cls,resolver,errors);self.assertEqual([],errors)

    def test_setter_rejects_bad_type_argument_count_and_static_owner(self):
        annotation={'annotation':'Lorg/spongepowered/asm/mixin/gen/Accessor;','value':'colorTexture'}
        for descriptor in ['(I)V','(LGpuTexture;I)V','(LGpuTexture;)LGpuTexture;','()V']:
            self.assertTrue(self.audit(descriptor,annotation),descriptor)
        self.assertTrue(self.audit('(LGpuTexture;)V',annotation,True))

    def test_modifyarg_rejects_wrong_index_or_type_and_checks_invoke_site(self):
        annotation={'annotation':'Lorg/spongepowered/asm/mixin/injection/ModifyArg;','method':['render()V'],'index':1,
                    'at':[{'value':'INVOKE','target':'Lgame/Projection;perspective(FFFF)LMatrix;'}]}
        self.assertEqual([],self.audit('(F)F',annotation))
        self.assertTrue(self.audit('(D)D',annotation))
        self.assertTrue(self.audit('(F)F',dict(annotation,index=4)))
        wrong=dict(annotation,at=[{'value':'INVOKE','target':'Lgame/Projection;missing(FFFF)LMatrix;'}])
        self.assertTrue(self.audit('(F)F',wrong))

    def test_modifyargs_requires_args_and_void_return(self):
        annotation={'annotation':'Lorg/spongepowered/asm/mixin/injection/ModifyArgs;','method':['render()V'],
                    'at':[{'value':'INVOKE','target':'Lgame/Projection;perspective(FFFF)LMatrix;'}]}
        self.assertEqual([],self.audit('(Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;)V',annotation))
        self.assertTrue(self.audit('(F)V',annotation))


if __name__=='__main__':
    unittest.main()
